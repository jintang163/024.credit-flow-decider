package com.bc.credit.integration.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bc.credit.common.enums.CreditDataSourceType;
import com.bc.credit.common.enums.DataQualityTag;
import com.bc.credit.common.enums.QueryMode;
import com.bc.credit.dto.credit.CreditQueryRequest;
import com.bc.credit.dto.credit.CreditQueryResponse;
import com.bc.credit.entity.CreditApiCallLog;
import com.bc.credit.mapper.CreditApiCallLogMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class CreditApiCallLogService {

    @Autowired
    private CreditApiCallLogMapper creditApiCallLogMapper;

    @Async
    public void saveCallLogAsync(String queryId, CreditQueryRequest request,
                                 CreditDataSourceType dataSource, QueryMode queryMode,
                                 String requestBody, String responseBody,
                                 Long costMs, Integer retryCount,
                                 boolean success, String errorCode, String errorMsg,
                                 DataQualityTag qualityTag, CircuitBreaker circuitBreaker) {
        try {
            saveCallLog(queryId, request, dataSource, queryMode, requestBody, responseBody,
                    costMs, retryCount, success, errorCode, errorMsg, qualityTag, circuitBreaker);
        } catch (Exception e) {
            log.error("[调用日志] 异步保存日志失败, queryId: {}, dataSource: {}",
                    queryId, dataSource.getCode(), e);
        }
    }

    public void saveCallLog(String queryId, CreditQueryRequest request,
                            CreditDataSourceType dataSource, QueryMode queryMode,
                            String requestBody, String responseBody,
                            Long costMs, Integer retryCount,
                            boolean success, String errorCode, String errorMsg,
                            DataQualityTag qualityTag, CircuitBreaker circuitBreaker) {
        try {
            CreditApiCallLog logEntity = new CreditApiCallLog();
            logEntity.setId(IdWorker.getId());
            logEntity.setQueryId(queryId);
            logEntity.setRequestId(request.getRequestId());
            logEntity.setApplicationId(request.getApplicationId());
            logEntity.setApplicationNo(request.getApplicationNo());
            logEntity.setCustomerId(request.getCustomerId());
            logEntity.setDataSource(dataSource.getCode());
            logEntity.setDataSourceName(dataSource.getName());
            logEntity.setQueryMode(queryMode.getCode());
            logEntity.setRequestBody(truncate(requestBody, 4000));
            logEntity.setResponseBody(truncate(responseBody, 4000));
            logEntity.setCostMs(costMs);
            logEntity.setRetryCount(retryCount != null ? retryCount : 0);
            logEntity.setSuccess(success ? 1 : 0);
            logEntity.setErrorCode(errorCode);
            logEntity.setErrorMsg(truncate(errorMsg, 500));
            logEntity.setQualityTag(qualityTag != null ? qualityTag.getCode() : null);
            logEntity.setCircuitBreakerStatus(circuitBreaker != null ? circuitBreaker.getState().name() : null);
            logEntity.setCallTime(LocalDateTime.now());
            logEntity.setCreatedTime(LocalDateTime.now());
            logEntity.setDeleted(0);

            creditApiCallLogMapper.insert(logEntity);

            log.debug("[调用日志] 已保存征信API调用日志, queryId: {}, dataSource: {}, success: {}, cost: {}ms",
                    queryId, dataSource.getCode(), success, costMs);

        } catch (Exception e) {
            log.error("[调用日志] 保存征信API调用日志失败, queryId: {}, dataSource: {}",
                    queryId, dataSource.getCode(), e);
        }
    }

    public void saveQuerySummaryLog(String queryId, CreditQueryRequest request,
                                    CreditQueryResponse response, Map<String, CircuitBreaker> circuitBreakers) {
        try {
            for (Map.Entry<String, CreditQueryResponse.DataSourceResult> entry
                    : response.getDataSourceResults().entrySet()) {

                CreditDataSourceType dataSource = CreditDataSourceType.getByCode(entry.getKey());
                if (dataSource == null) continue;

                CreditQueryResponse.DataSourceResult result = entry.getValue();
                CircuitBreaker cb = circuitBreakers != null ? circuitBreakers.get(entry.getKey()) : null;

                saveCallLog(
                        queryId,
                        request,
                        dataSource,
                        response.isAsyncQuery() ? QueryMode.ASYNC : QueryMode.SYNC,
                        null,
                        result.getRawResponse(),
                        result.getCostMs(),
                        result.getRetryCount(),
                        result.isSuccess(),
                        null,
                        result.getErrorMsg(),
                        response.getQualityTag(),
                        cb
                );
            }

            log.info("[调用日志] 已保存查询汇总日志, queryId: {}, totalCost: {}ms, allSuccess: {}",
                    queryId, response.getTotalCostMs(), response.isAllSuccess());

        } catch (Exception e) {
            log.error("[调用日志] 保存查询汇总日志失败, queryId: {}", queryId, e);
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...[truncated]";
    }
}
