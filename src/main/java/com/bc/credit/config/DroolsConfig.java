package com.bc.credit.config;

import com.bc.credit.engine.impl.DroolsKieContainerManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;

@Slf4j
@Configuration
@EnableScheduling
public class DroolsConfig {

    @Autowired
    private DroolsKieContainerManager kieContainerManager;

    @Value("${credit.anti-fraud.drools.auto-reload:false}")
    private boolean autoReload;

    @Value("${credit.anti-fraud.drools.reload-cron:0 */5 * * * ?}")
    private String reloadCron;

    @Value("${credit.anti-fraud.drools.enabled:true}")
    private boolean droolsEnabled;

    @PostConstruct
    public void init() {
        log.info("DroolsConfig initialized, droolsEnabled: {}, autoReload: {}", droolsEnabled, autoReload);
    }

    @Scheduled(fixedDelayString = "${credit.anti-fraud.drools.reload-interval-ms:300000}")
    public void scheduledReload() {
        if (autoReload && droolsEnabled) {
            try {
                log.info("Scheduled Drools rule reload started");
                kieContainerManager.reloadAll();
                log.info("Scheduled Drools rule reload completed");
            } catch (Exception e) {
                log.error("Scheduled Drools rule reload failed", e);
            }
        }
    }

    @Bean
    public DroolsRuleRefreshListener droolsRuleRefreshListener() {
        return new DroolsRuleRefreshListener(kieContainerManager);
    }

    public static class DroolsRuleRefreshListener {

        private final DroolsKieContainerManager kieContainerManager;

        public DroolsRuleRefreshListener(DroolsKieContainerManager kieContainerManager) {
            this.kieContainerManager = kieContainerManager;
        }

        public void onRefresh(String group) {
            log.info("Drools rule refresh triggered for group: {}", group);
            kieContainerManager.reloadRuleGroup(group);
        }

        public void onRefreshAll() {
            log.info("Drools rule refresh all triggered");
            kieContainerManager.reloadAll();
        }
    }
}
