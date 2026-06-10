package com.bc.credit.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrometheusMetricsConfig {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Bean
    public Gauge runningProcessInstancesGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("credit_flow_process_instances_running",
                        () -> runtimeService.createProcessInstanceQuery().active().count())
                .description("当前运行中的流程实例数")
                .tag("status", "running")
                .register(meterRegistry);
    }

    @Bean
    public Gauge completedProcessInstancesGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("credit_flow_process_instances_completed",
                        () -> historyService.createHistoricProcessInstanceQuery().finished().count())
                .description("已完成流程实例总数")
                .tag("status", "completed")
                .register(meterRegistry);
    }

    @Bean
    public Counter processInstanceStartedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("credit_flow_process_started_total")
                .description("流程实例启动总数")
                .register(meterRegistry);
    }

    @Bean
    public Counter creditQueryRetryCounter(MeterRegistry meterRegistry) {
        return Counter.builder("credit_flow_credit_query_retry_total")
                .description("征信查询重试次数")
                .register(meterRegistry);
    }

    @Bean
    public Counter opsSkipNodeCounter(MeterRegistry meterRegistry) {
        return Counter.builder("credit_flow_ops_skip_node_total")
                .description("运维跳过节点次数")
                .register(meterRegistry);
    }

    @Bean
    public Timer creditQueryTimer(MeterRegistry meterRegistry) {
        return Timer.builder("credit_flow_credit_query_duration")
                .description("征信查询耗时")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
}
