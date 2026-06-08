package com.bc.credit.dto.credit;

import com.bc.credit.common.enums.DataQualityTag;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class StructuredCreditData implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;

    private String idCard;

    private Integer multiLendingCount;

    private Integer overdueDays;

    private BigDecimal totalDebtRatio;

    private BigDecimal incomeReliability;

    private Integer courtExecutionCount;

    private List<String> courtExecutionDetails;

    private DataQualityTag qualityTag;

    private Map<String, Boolean> dataSourceStatus;

    private LocalDateTime queryTime;

    private String queryId;

    public boolean isPendingReview() {
        return DataQualityTag.PENDING_REVIEW.equals(qualityTag)
                || DataQualityTag.FALLBACK.equals(qualityTag)
                || DataQualityTag.PARTIAL.equals(qualityTag);
    }
}
