package com.bc.credit.engine.impl;

import lombok.extern.slf4j.Slf4j;
import org.drools.core.util.IoUtils;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DroolsKieContainerManager {

    private static final String REDIS_RULE_PREFIX = "rule:drl:";

    @Value("${credit.anti-fraud.drools.rule-packages:com.bc.credit.fraud}")
    private String fraudRulePackages;

    @Value("${credit.limit.drools.rule-packages:com.bc.credit.limit}")
    private String limitRulePackages;

    @Value("${credit.anti-fraud.drools.scan-interval:30}")
    private int scanIntervalSeconds;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final Map<String, KieContainer> kieContainerMap = new ConcurrentHashMap<>();
    private final Map<String, KieBase> kieBaseMap = new ConcurrentHashMap<>();
    private final Map<String, Long> kieBaseVersionMap = new ConcurrentHashMap<>();
    private final Map<String, String> groupPackageMap = new ConcurrentHashMap<>();

    private KieServices kieServices;

    @PostConstruct
    public void init() {
        kieServices = KieServices.Factory.get();

        loadRuleGroupFromFiles("default", fraudRulePackages);

        loadRuleGroupFromRedis("A", fraudRulePackages);
        loadRuleGroupFromRedis("B", fraudRulePackages);

        loadRuleGroupFromFiles("limit", limitRulePackages);

        log.info("DroolsKieContainerManager initialized, fraud packages: {}, limit packages: {}",
                fraudRulePackages, limitRulePackages);
    }

    public synchronized void loadAllRuleGroups() {
        loadRuleGroupFromFiles("default", fraudRulePackages);
        loadRuleGroupFromRedis("A", fraudRulePackages);
        loadRuleGroupFromRedis("B", fraudRulePackages);
        loadRuleGroupFromFiles("limit", limitRulePackages);
    }

    public void loadRuleGroupFromFiles(String group, String packages) {
        loadRuleGroupFromFiles(group, packages, null);
    }

    public void loadRuleGroupFromFiles(String group, String packages, String overrideDrl) {
        try {
            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
            String kmoduleXml = buildKmoduleXml(group, packages);
            kieFileSystem.writeKModuleXML(kmoduleXml);

            if (overrideDrl != null && !overrideDrl.isEmpty()) {
                String path = "src/main/resources/rules/dynamic/" + group + "/override.drl";
                kieFileSystem.write(path, overrideDrl);
                groupPackageMap.put(group, packages);
                buildKieContainer(group, kieFileSystem);
                return;
            }

            String[] pkgArray = packages.split(",");
            int ruleIndex = 0;
            for (String pkg : pkgArray) {
                String pkgPath = pkg.trim().replace('.', '/');
                String resourcePath = "rules/" + pkgPath + "/";
                List<String> drlFiles = getResourcesInPath(resourcePath);

                for (String drlFile : drlFiles) {
                    String drlContent = readResourceContent(resourcePath + drlFile);
                    if (drlContent != null) {
                        String path = "src/main/resources/" + resourcePath + drlFile;
                        kieFileSystem.write(path, drlContent);
                        ruleIndex++;
                        log.debug("Loaded DRL file: {} for group: {}", drlFile, group);
                    }
                }
            }

            if (ruleIndex == 0) {
                String defaultRule = buildDefaultRule(group, packages);
                String path = "src/main/resources/rules/default/" + group + "_default.drl";
                kieFileSystem.write(path, defaultRule);
            }

            groupPackageMap.put(group, packages);
            buildKieContainer(group, kieFileSystem);
        } catch (Exception e) {
            log.error("Failed to load rule group from files: {}", group, e);
        }
    }

    public void loadRuleGroupFromRedis(String group, String packages) {
        if (stringRedisTemplate == null) {
            log.debug("Redis not available, skip loading rule group from Redis: {}", group);
            return;
        }

        try {
            Set<String> keys = stringRedisTemplate.keys(REDIS_RULE_PREFIX + group + ":*");
            if (keys == null || keys.isEmpty()) {
                log.debug("No DRL rules found in Redis for group: {}", group);
                return;
            }

            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
            String kmoduleXml = buildKmoduleXml(group, packages);
            kieFileSystem.writeKModuleXML(kmoduleXml);

            for (String key : keys) {
                String drlContent = stringRedisTemplate.opsForValue().get(key);
                if (drlContent != null) {
                    String fileName = key.substring(key.lastIndexOf(':') + 1);
                    String path = "src/main/resources/rules/redis/" + group + "/" + fileName;
                    kieFileSystem.write(path, drlContent);
                }
            }

            groupPackageMap.put(group, packages);
            buildKieContainer(group, kieFileSystem);
            log.info("Loaded rule group from Redis: {}, rules count: {}", group, keys.size());
        } catch (Exception e) {
            log.error("Failed to load rule group from Redis: {}", group, e);
        }
    }

    public void loadRuleFromString(String group, String packages, String ruleName, String drlContent) {
        try {
            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
            String kmoduleXml = buildKmoduleXml(group, packages);
            kieFileSystem.writeKModuleXML(kmoduleXml);

            String path = "src/main/resources/rules/dynamic/" + group + "/" + ruleName + ".drl";
            kieFileSystem.write(path, drlContent);

            groupPackageMap.put(group, packages);
            buildKieContainer(group, kieFileSystem);
            log.info("Loaded dynamic rule: {} for group: {}", ruleName, group);
        } catch (Exception e) {
            log.error("Failed to load rule from string, group: {}, ruleName: {}", group, ruleName, e);
            throw new RuntimeException("Failed to load rule: " + e.getMessage(), e);
        }
    }

    private void buildKieContainer(String group, KieFileSystem kieFileSystem) {
        ReleaseId releaseId = kieServices.newReleaseId("com.bc.credit", "rules-" + group, UUID.randomUUID().toString());
        kieFileSystem.generateAndWritePomXML(releaseId);

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        Results results = kieBuilder.getResults();
        if (results.hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
            log.error("Drools compilation errors for group {}: {}", group, results);
            throw new RuntimeException("DRL compilation failed: " + results.toString());
        }

        KieModule kieModule = kieBuilder.getKieModule();
        KieContainer kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());

        kieContainerMap.put(group, kieContainer);
        kieBaseMap.put(group, kieContainer.getKieBase(group + "KBase"));
        kieBaseVersionMap.put(group, System.currentTimeMillis());

        log.info("KieContainer built for group: {}, version: {}", group, kieBaseVersionMap.get(group));
    }

    public KieBase getKieBase(String group) {
        return kieBaseMap.get(group);
    }

    public void reloadRuleGroup(String group) {
        log.info("Reloading rule group: {}", group);
        KieContainer oldContainer = kieContainerMap.remove(group);
        kieBaseMap.remove(group);
        kieBaseVersionMap.remove(group);

        if (oldContainer != null) {
            oldContainer.dispose();
        }

        String packages = groupPackageMap.getOrDefault(group, fraudRulePackages);

        if ("A".equals(group) || "B".equals(group)) {
            loadRuleGroupFromRedis(group, packages);
        } else {
            loadRuleGroupFromFiles(group, packages);
        }
    }

    public void reloadAll() {
        log.info("Reloading all rule groups");
        for (String group : new ArrayList<>(kieContainerMap.keySet())) {
            try {
                reloadRuleGroup(group);
            } catch (Exception e) {
                log.error("Failed to reload rule group: {}", group, e);
            }
        }
    }

    public void saveDrlToRedis(String group, String ruleName, String drlContent) {
        if (stringRedisTemplate != null) {
            String key = REDIS_RULE_PREFIX + group + ":" + ruleName;
            stringRedisTemplate.opsForValue().set(key, drlContent);
            log.info("Saved DRL to Redis, group: {}, ruleName: {}", group, ruleName);
        }
    }

    public void removeDrlFromRedis(String group, String ruleName) {
        if (stringRedisTemplate != null) {
            String key = REDIS_RULE_PREFIX + group + ":" + ruleName;
            stringRedisTemplate.delete(key);
            log.info("Removed DRL from Redis, group: {}, ruleName: {}", group, ruleName);
        }
    }

    public boolean validateDrl(String drlContent) {
        return validateDrl(drlContent, fraudRulePackages);
    }

    public boolean validateDrl(String drlContent, String packages) {
        try {
            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
            kieFileSystem.writeKModuleXML(buildKmoduleXml("validate", packages));
            kieFileSystem.write("src/main/resources/rules/validate/test.drl", drlContent);

            ReleaseId releaseId = kieServices.newReleaseId("com.bc.credit", "validate", UUID.randomUUID().toString());
            kieFileSystem.generateAndWritePomXML(releaseId);

            KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
            kieBuilder.buildAll();

            Results results = kieBuilder.getResults();
            return !results.hasMessages(org.kie.api.builder.Message.Level.ERROR);
        } catch (Exception e) {
            log.warn("DRL validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getRuleGroupStatus() {
        Map<String, Object> status = new HashMap<>();
        for (Map.Entry<String, KieContainer> entry : kieContainerMap.entrySet()) {
            Map<String, Object> groupInfo = new HashMap<>();
            groupInfo.put("version", kieBaseVersionMap.get(entry.getKey()));
            groupInfo.put("packages", groupPackageMap.get(entry.getKey()));
            KieBase kieBase = kieBaseMap.get(entry.getKey());
            if (kieBase != null) {
                int ruleCount = 0;
                for (org.kie.api.definition.KiePackage pkg : kieBase.getKiePackages()) {
                    ruleCount += pkg.getRules().size();
                }
                groupInfo.put("ruleCount", ruleCount);
            }
            status.put(entry.getKey(), groupInfo);
        }
        return status;
    }

    private String buildKmoduleXml(String group, String packages) {
        String packagesAttr = packages != null ? packages : "com.bc.credit.fraud";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<kmodule xmlns=\"http://www.drools.org/xsd/kmodule\">\n" +
                "  <kbase name=\"" + group + "KBase\" packages=\"" + packagesAttr + "\" default=\"false\">\n" +
                "    <ksession name=\"" + group + "KSession\" default=\"false\"/>\n" +
                "  </kbase>\n" +
                "</kmodule>";
    }

    private String buildDefaultRule(String group, String packages) {
        String pkg = packages != null ? packages.split(",")[0].trim() : "com.bc.credit.fraud";
        return "package " + pkg + "\n\n" +
                "rule \"default_pass_" + group + "\"\n" +
                "  @ruleCode(\"DEFAULT_" + group + "\")\n" +
                "  @ruleType(\"DEFAULT\")\n" +
                "  @score(0)\n" +
                "  @riskLevel(\"LOW\")\n" +
                "  @action(\"PASS\")\n" +
                "  when\n" +
                "    $fact : AntiFraudRuleFact()\n" +
                "  then\n" +
                "    $fact.setHit(false);\n" +
                "end\n";
    }

    private List<String> getResourcesInPath(String path) {
        List<String> files = new ArrayList<>();
        try {
            Enumeration<java.net.URL> resources = getClass().getClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                java.net.URL url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    java.io.File dir = new java.io.File(url.toURI());
                    if (dir.isDirectory()) {
                        for (java.io.File file : dir.listFiles()) {
                            if (file.getName().endsWith(".drl")) {
                                files.add(file.getName());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("No resources found in path: {}", path);
        }
        return files;
    }

    private String readResourceContent(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                return new String(IoUtils.readBytesFromInputStream(is), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.debug("Failed to read resource: {}", path);
        }
        return null;
    }
}
