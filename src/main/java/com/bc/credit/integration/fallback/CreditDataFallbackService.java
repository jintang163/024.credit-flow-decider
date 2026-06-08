package com.bc.credit.integration.fallback;

import com.bc.credit.common.enums.DataQualityTag;
import com.bc.credit.dto.credit.CreditQueryRequest;
import com.bc.credit.dto.credit.StructuredCreditData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CreditDataFallbackService {

    public StructuredCreditData getFallbackData(CreditQueryRequest request, String dataSource, Throwable cause) {
        log.warn("[降级策略] 数据源{}调用失败，返回保守默认值, 原因: {}",
                dataSource, cause != null ? cause.getMessage() : "unknown");

        StructuredCreditData fallbackData = new StructuredCreditData();
        fallbackData.setCustomerId(request.getCustomerId());
        fallbackData.setIdCard(request.getIdCard());
        fallbackData.setQueryTime(LocalDateTime.now());
        fallbackData.setQueryId(java.util.UUID.randomUUID().toString().replace("-", ""));
        fallbackData.setQualityTag(DataQualityTag.FALLBACK);

        fallbackData.setMultiLendingCount(Integer.MAX_VALUE);
        fallbackData.setOverdueDays(Integer.MAX_VALUE);
        fallbackData.setTotalDebtRatio(new BigDecimal("1.00"));
        fallbackData.setIncomeReliability(new BigDecimal("0.00"));
        fallbackData.setCourtExecutionCount(Integer.MAX_VALUE);
        fallbackData.setCourtExecutionDetails(new java.util.ArrayList<>());

        Map<String, Boolean> status = new HashMap<>();
        status.put(dataSource, false);
        fallbackData.setDataSourceStatus(status);

        log.warn("[降级策略] 已返回保守默认值, multiLendingCount: {}, overdueDays: {}, debtRatio: {}, " +
                        "incomeReliability: {}, courtExecutionCount: {}",
                fallbackData.getMultiLendingCount(),
                fallbackData.getOverdueDays(),
                fallbackData.getTotalDebtRatio(),
                fallbackData.getIncomeReliability(),
                fallbackData.getCourtExecutionCount());

        return fallbackData;
    }

    public StructuredCreditData getPendingReviewData(CreditQueryRequest request, StructuredCreditData partialData) {
        log.warn("[降级策略] 部分数据源失败，数据打标'待人工复核', customerId: {}",
                request.getCustomerId());

        if (partialData == null) {
            partialData = getFallbackData(request, "UNKNOWN", null);
        }

        partialData.setQualityTag(DataQualityTag.PENDING_REVIEW);

        log.warn("[降级策略] 数据已打标'待人工复核', qualityTag: {}, pendingReview: {}",
                partialData.getQualityTag(), partialData.isPendingReview());

        return partialData;
    }

    public StructuredCreditData mergeWithFallback(StructuredCreditData primaryData,
                                                  StructuredCreditData fallbackData,
                                                  String failedDataSource) {
        if (primaryData == null) {
            return fallbackData;
        }

        if (primaryData.getMultiLendingCount() == null) {
            primaryData.setMultiLendingCount(fallbackData.getMultiLendingCount());
        }
        if (primaryData.getOverdueDays() == null) {
            primaryData.setOverdueDays(fallbackData.getOverdueDays());
        }
        if (primaryData.getTotalDebtRatio() == null) {
            primaryData.setTotalDebtRatio(fallbackData.getTotalDebtRatio());
        }
        if (primaryData.getIncomeReliability() == null) {
            primaryData.setIncomeReliability(fallbackData.getIncomeReliability());
        }
        if (primaryData.getCourtExecutionCount() == null) {
            primaryData.setCourtExecutionCount(fallbackData.getCourtExecutionCount());
        }

        Map<String, Boolean> status = primaryData.getDataSourceStatus();
        if (status == null) {
            status = new HashMap<>();
        }
        status.put(failedDataSource, false);
        primaryData.setDataSourceStatus(status);

        if (primaryData.getQualityTag() == null || DataQualityTag.NORMAL.equals(primaryData.getQualityTag())) {
            primaryData.setQualityTag(DataQualityTag.PARTIAL);
        }

        log.info("[降级策略] 已合并降级数据, failedDataSource: {}, qualityTag: {}",
                failedDataSource, primaryData.getQualityTag());

        return primaryData;
    }
}
