package com.bc.credit.config;

import com.bc.credit.engine.impl.DroolsKieContainerManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DroolsRuleRefreshEndpoint {

    @Autowired
    private DroolsKieContainerManager kieContainerManager;

    @Autowired
    private DroolsConfig.DroolsRuleRefreshListener droolsRuleRefreshListener;

    @EventListener(RefreshEvent.class)
    public void onRefreshEvent(RefreshEvent event) {
        log.info("Spring Cloud Config refresh event received, reloading Drools rules");
        try {
            droolsRuleRefreshListener.onRefreshAll();
        } catch (Exception e) {
            log.error("Failed to reload Drools rules on config refresh", e);
        }
    }
}
