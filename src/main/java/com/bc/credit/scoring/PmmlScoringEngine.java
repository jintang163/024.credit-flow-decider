package com.bc.credit.scoring;

import com.bc.credit.dto.ScoringFeatureInputDTO;
import lombok.extern.slf4j.Slf4j;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.EvaluatorUtil;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.ProbabilityDistribution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PmmlScoringEngine {

    @Value("${credit.scoring.pmml.model-path:models/}")
    private String modelPath;

    @Value("${credit.scoring.pmml.model-version:V1.0}")
    private String modelVersion;

    @Value("${credit.scoring.pmml.auto-reload:false}")
    private boolean autoReload;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_MODEL_KEY = "anti-fraud:pmml:model:";

    private final Map<String, Evaluator> evaluatorMap = new ConcurrentHashMap<>();
    private final Map<String, Long> evaluatorLoadTime = new ConcurrentHashMap<>();
    private volatile String activeModelVersion;

    @PostConstruct
    public void init() {
        loadModel(modelVersion);
        activeModelVersion = modelVersion;
        log.info("PmmlScoringEngine initialized, modelVersion: {}, modelPath: {}", modelVersion, modelPath);
    }

    @PreDestroy
    public void destroy() {
        evaluatorMap.clear();
        evaluatorLoadTime.clear();
    }

    public void loadModel(String version) {
        try {
            Evaluator evaluator = loadFromRedis(version);

            if (evaluator == null) {
                evaluator = loadFromFileSystem(version);
            }

            if (evaluator != null) {
                evaluatorMap.put(version, evaluator);
                evaluatorLoadTime.put(version, System.currentTimeMillis());
                log.info("PMML model loaded successfully, version: {}", version);
            } else {
                log.warn("No PMML model found for version: {}", version);
            }
        } catch (Exception e) {
            log.error("Failed to load PMML model, version: {}", version, e);
        }
    }

    private Evaluator loadFromRedis(String version) {
        if (stringRedisTemplate == null) {
            return null;
        }
        try {
            String modelContent = stringRedisTemplate.opsForValue().get(REDIS_MODEL_KEY + version);
            if (modelContent != null) {
                byte[] bytes = Base64.getDecoder().decode(modelContent);
                try (InputStream is = new ByteArrayInputStream(bytes)) {
                    PMML pmml = org.jpmml.model.PMMLUtil.unmarshal(is);
                    Evaluator evaluator = new LoadingModelEvaluatorBuilder()
                            .pmml(pmml)
                            .build();
                    evaluator.verify();
                    log.info("PMML model loaded from Redis, version: {}", version);
                    return evaluator;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load PMML model from Redis, version: {}", version, e);
        }
        return null;
    }

    private Evaluator loadFromFileSystem(String version) {
        try {
            String fileName = version + ".pmml";
            Path localPath = Paths.get(modelPath, fileName);

            if (Files.exists(localPath)) {
                try (InputStream is = new FileInputStream(localPath.toFile())) {
                    Evaluator evaluator = new LoadingModelEvaluatorBuilder()
                            .load(localPath.toFile())
                            .build();
                    evaluator.verify();
                    cacheModelToRedis(version, localPath.toFile());
                    log.info("PMML model loaded from file system, path: {}", localPath);
                    return evaluator;
                }
            }

            Path classpathPath = Paths.get("models", fileName);
            InputStream classpathIs = getClass().getClassLoader().getResourceAsStream(classpathPath.toString());
            if (classpathIs != null) {
                try (InputStream is = classpathIs) {
                    PMML pmml = org.jpmml.model.PMMLUtil.unmarshal(is);
                    Evaluator evaluator = new LoadingModelEvaluatorBuilder()
                            .pmml(pmml)
                            .build();
                    evaluator.verify();
                    log.info("PMML model loaded from classpath, path: {}", classpathPath);
                    return evaluator;
                }
            }

            File defaultFile = new File(modelPath);
            if (defaultFile.isDirectory()) {
                File[] pmmlFiles = defaultFile.listFiles((dir, name) -> name.endsWith(".pmml"));
                if (pmmlFiles != null && pmmlFiles.length > 0) {
                    try (InputStream is = new FileInputStream(pmmlFiles[0])) {
                        Evaluator evaluator = new LoadingModelEvaluatorBuilder()
                                .load(pmmlFiles[0])
                                .build();
                        evaluator.verify();
                        log.info("PMML model loaded from default directory, file: {}", pmmlFiles[0].getName());
                        return evaluator;
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to load PMML model from file system, version: {}", version, e);
        }
        return null;
    }

    private void cacheModelToRedis(String version, File file) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String encoded = Base64.getEncoder().encodeToString(bytes);
            stringRedisTemplate.opsForValue().set(REDIS_MODEL_KEY + version, encoded);
            log.info("PMML model cached to Redis, version: {}, size: {} bytes", version, bytes.length);
        } catch (Exception e) {
            log.warn("Failed to cache PMML model to Redis, version: {}", version, e);
        }
    }

    public PmmlScoringResult evaluate(ScoringFeatureInputDTO input) {
        Evaluator evaluator = evaluatorMap.get(activeModelVersion);
        if (evaluator == null) {
            throw new RuntimeException("No PMML model loaded, activeVersion: " + activeModelVersion);
        }

        Map<String, FieldValue> arguments = new LinkedHashMap<>();

        List<org.jpmml.evaluator.InputField> inputFields = evaluator.getInputFields();
        for (org.jpmml.evaluator.InputField inputField : inputFields) {
            String fieldName = inputField.getName();
            Object value = getFeatureValue(input, fieldName);
            FieldValue fieldValue = inputField.prepare(value);
            arguments.put(fieldName, fieldValue);
        }

        Map<String, ?> results = evaluator.evaluate(arguments);
        Map<String, ?> decodedResults = EvaluatorUtil.decodeAll(results);

        PmmlScoringResult scoringResult = new PmmlScoringResult();

        for (Map.Entry<String, ?> entry : decodedResults.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof ProbabilityDistribution) {
                ProbabilityDistribution probDist = (ProbabilityDistribution) value;
                scoringResult.setDefaultProbability(probDist.getProbability("1"));

                try {
                    Object predicted = probDist.getResult();
                    scoringResult.setPredictedClass(String.valueOf(predicted));
                } catch (Exception e) {
                    scoringResult.setPredictedClass("0");
                }
            } else if (value instanceof Number) {
                scoringResult.setRawScore(((Number) value).doubleValue());
            } else {
                scoringResult.setPredictedClass(String.valueOf(value));
            }
        }

        scoringResult.setModelVersion(activeModelVersion);
        return scoringResult;
    }

    private Object getFeatureValue(ScoringFeatureInputDTO input, String fieldName) {
        switch (fieldName.toLowerCase()) {
            case "overduecount":
            case "overdue_count":
                return input.getOverdueCount() != null ? input.getOverdueCount() : 0;
            case "overdueamount":
            case "overdue_amount":
                return input.getOverdueAmount() != null ? input.getOverdueAmount() : 0;
            case "accountcount":
            case "account_count":
                return input.getAccountCount() != null ? input.getAccountCount() : 0;
            case "creditcardcount":
            case "credit_card_count":
                return input.getCreditCardCount() != null ? input.getCreditCardCount() : 0;
            case "creditcardutilization":
            case "credit_card_utilization":
                return input.getCreditCardUtilization() != null ? input.getCreditCardUtilization().doubleValue() : 0.0;
            case "creditquerycount3m":
            case "credit_query_count_3m":
                return input.getCreditQueryCount3m() != null ? input.getCreditQueryCount3m() : 0;
            case "maxoverduedays":
            case "max_overdue_days":
                return input.getMaxOverdueDays() != null ? input.getMaxOverdueDays() : 0;
            case "age":
                return input.getAge() != null ? input.getAge() : 0;
            case "monthlyincome":
            case "monthly_income":
                return input.getMonthlyIncome() != null ? input.getMonthlyIncome().doubleValue() : 0.0;
            case "workyears":
            case "work_years":
                return input.getWorkYears() != null ? input.getWorkYears() : 0;
            case "educationlevel":
            case "education_level":
                return input.getEducationLevel() != null ? input.getEducationLevel() : 0;
            case "hashouse":
            case "has_house":
                return input.getHasHouse() != null && input.getHasHouse() ? 1 : 0;
            case "hascar":
            case "has_car":
                return input.getHasCar() != null && input.getHasCar() ? 1 : 0;
            case "maritalstatus":
            case "marital_status":
                return input.getMaritalStatus() != null ? input.getMaritalStatus() : "UNKNOWN";
            case "loanamount":
            case "loan_amount":
                return input.getLoanAmount() != null ? input.getLoanAmount().doubleValue() : 0.0;
            case "loanterm":
            case "loan_term":
                return input.getLoanTerm() != null ? input.getLoanTerm() : 0;
            case "monthlydebt":
            case "monthly_debt":
                return input.getMonthlyDebt() != null ? input.getMonthlyDebt().doubleValue() : 0.0;
            case "debtratio":
            case "debt_ratio":
                return input.getDebtRatio() != null ? input.getDebtRatio().doubleValue() : 0.0;
            case "filldurationseconds":
            case "fill_duration_seconds":
                return input.getFillDurationSeconds() != null ? input.getFillDurationSeconds() : 0;
            case "applyhour":
            case "apply_hour":
                return input.getApplyHour() != null ? input.getApplyHour() : 12;
            case "isweekendapply":
            case "is_weekend_apply":
                return input.getIsWeekendApply() != null && input.getIsWeekendApply() ? 1 : 0;
            case "channel":
                return input.getChannel() != null ? input.getChannel() : "UNKNOWN";
            default:
                log.debug("Unknown PMML feature field: {}, using null", fieldName);
                return null;
        }
    }

    public void reloadModel() {
        loadModel(activeModelVersion);
        log.info("PMML model reloaded, version: {}", activeModelVersion);
    }

    public void switchModel(String version) {
        if (!evaluatorMap.containsKey(version)) {
            loadModel(version);
        }
        if (evaluatorMap.containsKey(version)) {
            activeModelVersion = version;
            log.info("PMML model switched to version: {}", version);
        } else {
            throw new RuntimeException("Failed to switch PMML model, version not available: " + version);
        }
    }

    public void uploadModelToRedis(String version, byte[] pmmlContent) {
        if (stringRedisTemplate != null) {
            String encoded = Base64.getEncoder().encodeToString(pmmlContent);
            stringRedisTemplate.opsForValue().set(REDIS_MODEL_KEY + version, encoded);
            log.info("PMML model uploaded to Redis, version: {}, size: {} bytes", version, pmmlContent.length);
        }
    }

    public Map<String, Object> getModelStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeVersion", activeModelVersion);
        status.put("loadedVersions", new ArrayList<>(evaluatorMap.keySet()));
        status.put("loadTimes", new HashMap<>(evaluatorLoadTime));
        status.put("modelPath", modelPath);
        return status;
    }

    public String getActiveModelVersion() {
        return activeModelVersion;
    }

    public static class PmmlScoringResult {
        private double defaultProbability;
        private double rawScore;
        private String predictedClass;
        private String modelVersion;

        public double getDefaultProbability() { return defaultProbability; }
        public void setDefaultProbability(double defaultProbability) { this.defaultProbability = defaultProbability; }
        public double getRawScore() { return rawScore; }
        public void setRawScore(double rawScore) { this.rawScore = rawScore; }
        public String getPredictedClass() { return predictedClass; }
        public void setPredictedClass(String predictedClass) { this.predictedClass = predictedClass; }
        public String getModelVersion() { return modelVersion; }
        public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    }
}
