package com.bc.credit.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class LimitDistributionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String range;

    private Long count;

    private BigDecimal percentage;

    private BigDecimal avgAmount;
}
