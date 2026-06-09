package com.bc.credit.scoring;

import com.bc.credit.dto.ScoringFeatureInputDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PythonScoringClient {

    @Value("${credit.scoring.python-service.enabled:false}")
    private boolean enabled;

    @Value("${credit.scoring.python-service.base-url:http://localhost:5000}")
    private String baseUrl;

    @Value("${credit.scoring.python-service.api-key:}")
    private String apiKey;

    @Value("${credit.scoring.python-service.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${credit.scoring.python-service.include-shap:true}")
    private boolean includeShap;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        if (enabled) {
            this.webClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("X-API-Key", apiKey)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();
            log.info("PythonScoringClient initialized, baseUrl: {}", baseUrl);
        }
    }

    public PythonScoringResult score(ScoringFeatureInputDTO input) {
        if (!enabled || webClient == null) {
            throw new RuntimeException("Python scoring service is not enabled");
        }

        try {
            Map<String, Object> features = buildFeatureMap(input);

            JSONObject requestBody = new JSONObject();
            requestBody.put("customer_id", input.getCustomerId());
            requestBody.put("application_no", input.getApplicationNo());
            requestBody.put("features", features);
            requestBody.put("include_shap", includeShap);

            String responseJson = webClient.post()
                    .uri("/api/v1/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toJSONString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (responseJson == null) {
                throw new RuntimeException("Python scoring service returned null response");
            }

            JSONObject response = JSON.parseObject(responseJson);
            PythonScoringResult result = new PythonScoringResult();
            result.setScore(response.getIntValue("score"));
            result.setDefaultProbability(response.getDoubleValue("default_probability"));
            result.setModelVersion(response.getString("model_version"));
            result.setModelName(response.getString("model_name"));

            if (response.containsKey("shap_values")) {
                JSONObject shapObj = response.getJSONObject("shap_values");
                Map<String, BigDecimal> shapValues = new HashMap<>();
                for (Map.Entry<String, Object> entry : shapObj.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        shapValues.put(entry.getKey(), BigDecimal.valueOf(((Number) entry.getValue()).doubleValue()));
                    }
                }
                result.setShapValues(shapValues);
            }

            log.info("Python scoring service response, customerId: {}, score: {}, defaultProb: {}",
                    input.getCustomerId(), result.getScore(), result.getDefaultProbability());

            return result;
        } catch (Exception e) {
            log.error("Python scoring service call failed, customerId: {}", input.getCustomerId(), e);
            throw new RuntimeException("Python scoring service call failed: " + e.getMessage(), e);
        }
    }

    public boolean isHealthy() {
        if (!enabled || webClient == null) {
            return false;
        }
        try {
            String response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(2000))
                    .block();
            return response != null;
        } catch (Exception e) {
            log.warn("Python scoring service health check failed", e);
            return false;
        }
    }

    public String getModelInfo() {
        if (!enabled || webClient == null) {
            return "{}";
        }
        try {
            return webClient.get()
                    .uri("/api/v1/model/info")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
        } catch (Exception e) {
            log.warn("Python scoring service model info failed", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private Map<String, Object> buildFeatureMap(ScoringFeatureInputDTO input) {
        Map<String, Object> features = new HashMap<>();
        if (input.getOverdueCount() != null) features.put("overdue_count", input.getOverdueCount());
        if (input.getOverdueAmount() != null) features.put("overdue_amount", input.getOverdueAmount());
        if (input.getAccountCount() != null) features.put("account_count", input.getAccountCount());
        if (input.getCreditCardCount() != null) features.put("credit_card_count", input.getCreditCardCount());
        if (input.getCreditCardUtilization() != null) features.put("credit_card_utilization", input.getCreditCardUtilization());
        if (input.getCreditQueryCount3m() != null) features.put("credit_query_count_3m", input.getCreditQueryCount3m());
        if (input.getMaxOverdueDays() != null) features.put("max_overdue_days", input.getMaxOverdueDays());
        if (input.getAge() != null) features.put("age", input.getAge());
        if (input.getMonthlyIncome() != null) features.put("monthly_income", input.getMonthlyIncome());
        if (input.getWorkYears() != null) features.put("work_years", input.getWorkYears());
        if (input.getEducationLevel() != null) features.put("education_level", input.getEducationLevel());
        if (input.getHasHouse() != null) features.put("has_house", input.getHasHouse() ? 1 : 0);
        if (input.getHasCar() != null) features.put("has_car", input.getHasCar() ? 1 : 0);
        if (input.getMaritalStatus() != null) features.put("marital_status", input.getMaritalStatus());
        if (input.getLoanAmount() != null) features.put("loan_amount", input.getLoanAmount());
        if (input.getLoanTerm() != null) features.put("loan_term", input.getLoanTerm());
        if (input.getMonthlyDebt() != null) features.put("monthly_debt", input.getMonthlyDebt());
        if (input.getDebtRatio() != null) features.put("debt_ratio", input.getDebtRatio());
        if (input.getFillDurationSeconds() != null) features.put("fill_duration_seconds", input.getFillDurationSeconds());
        if (input.getApplyHour() != null) features.put("apply_hour", input.getApplyHour());
        if (input.getIsWeekendApply() != null) features.put("is_weekend_apply", input.getIsWeekendApply() ? 1 : 0);
        if (input.getChannel() != null) features.put("channel", input.getChannel());
        return features;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static class PythonScoringResult {
        private int score;
        private double defaultProbability;
        private String modelVersion;
        private String modelName;
        private Map<String, BigDecimal> shapValues;

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public double getDefaultProbability() { return defaultProbability; }
        public void setDefaultProbability(double defaultProbability) { this.defaultProbability = defaultProbability; }
        public String getModelVersion() { return modelVersion; }
        public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public Map<String, BigDecimal> getShapValues() { return shapValues; }
        public void setShapValues(Map<String, BigDecimal> shapValues) { this.shapValues = shapValues; }
    }
}
