package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreditQueryPercentileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String dataSource;

    private String startDate;

    private String endDate;

    private BigDecimal p50;

    private BigDecimal p90;

    private BigDecimal p95;

    private BigDecimal p99;

    private Long totalCount;

    private BigDecimal avgCostMs;

    private List<DataPointDTO> trend;
}
