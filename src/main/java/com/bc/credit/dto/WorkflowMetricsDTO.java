package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class WorkflowMetricsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long totalInstances;

    private Long runningInstances;

    private Long completedInstances;

    private Long suspendedInstances;

    private BigDecimal completionRate;

    private BigDecimal avgDurationMs;

    private Long todayStarted;

    private Long todayCompleted;
}
