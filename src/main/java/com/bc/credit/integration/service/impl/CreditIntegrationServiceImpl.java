package com.bc.credit.integration.service.impl;

import com.alibaba.fastjson2.JSON;
import com.bc.credit.common.enums.CreditDataSourceType;
import com.bc.credit.common.enums.DataQualityTag;
import com.bc.credit.common.enums.QueryMode;
import com.bc.credit.dto.credit.*;
import com.bc.credit.integration.adapter.CreditDataAdapter;
import com.bc.credit.integration.adapter.CreditDataAdapterFactory;
import com.bc.credit.integration.fallback.CreditDataFallbackService;
import com.bc.credit.integration.mq.CreditAsyncCallbackProducer;
import com.bc.credit.integration.service.CreditApiCallLogService;
import com.bc.credit.integration.service.CreditIntegrationService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Slf4j
@Service
public class CreditIntegrationServiceImpl implements CreditIntegrationService {

    @Autowired
    private CreditDataAdapterFactory adapterFactory;

    @Autowired
    private CreditDataFallbackService fallbackService;

    @Autowired
    private CreditApiCallLogService callLogService;

    @Autowired
    private CreditAsyncCallbackProducer callbackProducer;

    @Autowired
    private Map<String, CircuitBreaker> creditCircuitBreakers;

    @Autowired
    private Map<String, Retry> creditRetries;

    @Autowired
    private Map<String, TimeLimiter> creditTimeLimiters;

    private final ExecutorService executorService = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Override
    public CreditQueryResponse queryCredit(CreditQueryRequest request) {
        if (request.getQueryMode() == QueryMode.ASYNC) {
            return queryCreditAsync(request);
        }
        return queryCreditSync(request);
    }

    @Override
    public CreditQueryResponse queryCreditSync(CreditQueryRequest request) {
        String queryId = UUID.randomUUID().toString().replace("-", "");
        long totalStart = System.currentTimeMillis();
        log.info("[征信集成-同步] 开始查询, queryId: {}, customerId: {}, dataSources: {}",
                queryId, request.getCustomerId(), getDataSourceCodes(request.getDataSources()));

        if (request.getRequestId() == null) {
            request.setRequestId(queryId);
        }

        List<CreditDataSourceType> dataSources = request.getDataSources();
        if (dataSources == null || dataSources.isEmpty()) {
            dataSources = Arrays.asList(CreditDataSourceType.PBOC, CreditDataSourceType.BAIHANG);
        }

        Map<String, CreditQueryResponse.DataSourceResult> dataSourceResults = new ConcurrentHashMap<>();
        List<CompletableFuture<StructuredCreditData>> futures = new ArrayList<>();
        Map<String, AtomicInteger> retryCounts = new ConcurrentHashMap<>();

        for (CreditDataSourceType dataSource : dataSources) {
            retryCounts.put(dataSource.getCode(), new AtomicInteger(0));
            CompletableFuture<StructuredCreditData> future = CompletableFuture.supplyAsync(() ->
                    querySingleDataSource(queryId, request, dataSource, retryCounts.get(dataSource.getCode()), dataSourceResults),
                    executorService
            ).exceptionally(ex -> {
                log.warn("[征信集成-同步] 数据源{}查询异常, 返回降级数据, queryId: {}",
                        dataSource.getCode(), queryId, ex);
                return fallbackService.getFallbackData(request, dataSource.getCode(), ex);
            });
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

            List<StructuredCreditData> allData = new ArrayList<>();
            for (CompletableFuture<StructuredCreditData> future : futures) {
                try {
                    StructuredCreditData data = future.getNow(null);
                    if (data != null) {
                        allData.add(data);
                    }
                } catch (Exception e) {
                    log.warn("[征信集成-同步] 获取结果异常", e);
                }
            }

            long totalCost = System.currentTimeMillis() - totalStart;
            StructuredCreditData mergedData = mergeCreditData(allData, request);

            CreditQueryResponse response = new CreditQueryResponse();
            response.setRequestId(request.getRequestId());
            response.setQueryId(queryId);
            response.setSuccess(true);
            response.setMessage("查询完成");
            response.setData(mergedData);
            response.setQualityTag(mergedData.getQualityTag());
            response.setDataSourceResults(dataSourceResults);
            response.setTotalCostMs(totalCost);
            response.setQueryTime(LocalDateTime.now());
            response.setAsyncQuery(false);

            if (!response.isAllSuccess()) {
                fallbackService.getPendingReviewData(request, mergedData);
                response.setQualityTag(mergedData.getQualityTag());
                response.setMessage("部分数据源查询失败，数据需人工复核");
            }

            log.info("[征信集成-同步] 查询完成, queryId: {}, totalCost: {}ms, allSuccess: {}, qualityTag: {}",
                    queryId, totalCost, response.isAllSuccess(), response.getQualityTag());

            callLogService.saveQuerySummaryLog(queryId, request, response, creditCircuitBreakers);

            return response;

        } catch (TimeoutException e) {
            log.error("[征信集成-同步] 查询超时, queryId: {}", queryId, e);
            return buildTimeoutResponse(request, queryId, totalStart, dataSourceResults);
        } catch (Exception e) {
            log.error("[征信集成-同步] 查询异常, queryId: {}", queryId, e);
            return buildErrorResponse(request, queryId, totalStart, e, dataSourceResults);
        }
    }

    @Override
    public CreditQueryResponse queryCreditAsync(CreditQueryRequest request) {
        String queryId = UUID.randomUUID().toString().replace("-", "");
        log.info("[征信集成-异步] 接收异步查询请求, queryId: {}, customerId: {}",
                queryId, request.getCustomerId());

        if (request.getRequestId() == null) {
            request.setRequestId(queryId);
        }

        processAsyncQuery(queryId, request);

        CreditQueryResponse response = new CreditQueryResponse();
        response.setRequestId(request.getRequestId());
        response.setQueryId(queryId);
        response.setSuccess(true);
        response.setMessage("异步查询已提交，结果将通过MQ通知");
        response.setQueryTime(LocalDateTime.now());
        response.setAsyncQuery(true);

        log.info("[征信集成-异步] 异步查询已提交, queryId: {}", queryId);
        return response;
    }

    @Async("creditAsyncExecutor")
    public void processAsyncQuery(String queryId, CreditQueryRequest request) {
        log.info("[征信集成-异步处理] 开始处理, queryId: {}", queryId);

        CreditQueryResponse response = queryCreditSync(request);

        CreditAsyncCallbackMessage callbackMessage = new CreditAsyncCallbackMessage();
        callbackMessage.setRequestId(request.getRequestId());
        callbackMessage.setQueryId(queryId);
        callbackMessage.setApplicationId(String.valueOf(request.getApplicationId()));
        callbackMessage.setApplicationNo(request.getApplicationNo());
        callbackMessage.setCustomerId(request.getCustomerId());
        callbackMessage.setCreditData(response.getData());
        callbackMessage.setSuccess(response.isSuccess());
        callbackMessage.setErrorMsg(response.getMessage());
        callbackMessage.setQueryTime(response.getQueryTime());
        callbackMessage.setCallbackTime(LocalDateTime.now());
        callbackMessage.setCallbackUrl(request.getCallbackUrl());

        boolean sent = callbackProducer.sendCallbackMessage(callbackMessage);
        log.info("[征信集成-异步处理] 回调消息发送结果: {}, queryId: {}", sent, queryId);
    }

    private StructuredCreditData querySingleDataSource(String queryId, CreditQueryRequest request,
                                                       CreditDataSourceType dataSource,
                                                       AtomicInteger retryCount,
                                                       Map<String, CreditQueryResponse.DataSourceResult> results) {
        String dsCode = dataSource.getCode();
        long start = System.currentTimeMillis();

        CreditQueryResponse.DataSourceResult dsResult = new CreditQueryResponse.DataSourceResult();
        dsResult.setDataSourceCode(dsCode);
        dsResult.setDataSourceName(dataSource.getName());

        try {
            CircuitBreaker circuitBreaker = creditCircuitBreakers.get(dsCode);
            Retry retry = creditRetries.get(dsCode);
            TimeLimiter timeLimiter = creditTimeLimiters.get(dsCode);

            CreditDataAdapter adapter = adapterFactory.getAdapter(dataSource);

            Supplier<StructuredCreditData> querySupplier = () -> {
                try {
                    retryCount.incrementAndGet();
                    StructuredCreditData data = adapter.query(request);
                    data.setQualityTag(DataQualityTag.NORMAL);
                    return data;
                } catch (Exception e) {
                    log.warn("[征信集成-数据源] {}调用失败, 重试次数: {}, queryId: {}",
                            dsCode, retryCount.get(), queryId, e);
                    throw new RuntimeException(e);
                }
            };

            Supplier<CompletionStage<StructuredCreditData>> futureSupplier = () ->
                    CompletableFuture.supplyAsync(querySupplier, executorService);

            Supplier<StructuredCreditData> decoratedSupplier = Decorators.ofSupplier(() -> {
                        try {
                            return timeLimiter.executeFutureSupplier(futureSupplier);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate();

            StructuredCreditData result = decoratedSupplier.get();

            long cost = System.currentTimeMillis() - start;
            dsResult.setSuccess(true);
            dsResult.setCostMs(cost);
            dsResult.setRetryCount(retryCount.get() - 1);
            dsResult.setRawResponse(JSON.toJSONString(result));
            results.put(dsCode, dsResult);

            log.info("[征信集成-数据源] {}查询成功, cost: {}ms, retry: {}, queryId: {}",
                    dsCode, cost, retryCount.get() - 1, queryId);

            callLogService.saveCallLogAsync(queryId, request, dataSource, QueryMode.SYNC,
                    JSON.toJSONString(request), JSON.toJSONString(result),
                    cost, retryCount.get() - 1, true, null, null,
                    result.getQualityTag(), circuitBreaker);

            return result;

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            CircuitBreaker circuitBreaker = creditCircuitBreakers.get(dsCode);

            dsResult.setSuccess(false);
            dsResult.setCostMs(cost);
            dsResult.setRetryCount(retryCount.get());
            dsResult.setErrorMsg(e.getMessage());
            results.put(dsCode, dsResult);

            log.error("[征信集成-数据源] {}查询失败, cost: {}ms, retry: {}, error: {}, queryId: {}",
                    dsCode, cost, retryCount.get(), e.getMessage(), queryId);

            callLogService.saveCallLogAsync(queryId, request, dataSource, QueryMode.SYNC,
                    JSON.toJSONString(request), null,
                    cost, retryCount.get(), false, null, e.getMessage(),
                    DataQualityTag.FALLBACK, circuitBreaker);

            return fallbackService.getFallbackData(request, dsCode, e);
        }
    }

    private StructuredCreditData mergeCreditData(List<StructuredCreditData> dataList, CreditQueryRequest request) {
        if (dataList == null || dataList.isEmpty()) {
            return fallbackService.getFallbackData(request, "ALL", null);
        }

        StructuredCreditData result = new StructuredCreditData();
        result.setCustomerId(request.getCustomerId());
        result.setIdCard(request.getIdCard());
        result.setQueryTime(LocalDateTime.now());
        result.setQueryId(UUID.randomUUID().toString().replace("-", ""));

        Map<String, Boolean> dataSourceStatus = new HashMap<>();
        boolean hasFallback = false;
        boolean hasPartial = false;

        int validMultiLendingCount = 0;
        int validOverdueDays = 0;
        BigDecimal validTotalDebtRatio = BigDecimal.ZERO;
        BigDecimal validIncomeReliability = BigDecimal.ZERO;
        int validCourtExecutionCount = 0;
        int validCount = 0;

        List<String> allCourtDetails = new ArrayList<>();

        for (StructuredCreditData data : dataList) {
            if (data == null) continue;

            if (data.getDataSourceStatus() != null) {
                dataSourceStatus.putAll(data.getDataSourceStatus());
            }

            if (data.getQualityTag() == DataQualityTag.FALLBACK) {
                hasFallback = true;
                continue;
            }

            if (data.getQualityTag() == DataQualityTag.PARTIAL) {
                hasPartial = true;
            }

            validCount++;

            if (data.getMultiLendingCount() != null && data.getMultiLendingCount() < Integer.MAX_VALUE) {
                validMultiLendingCount = Math.max(validMultiLendingCount, data.getMultiLendingCount());
            }
            if (data.getOverdueDays() != null && data.getOverdueDays() < Integer.MAX_VALUE) {
                validOverdueDays = Math.max(validOverdueDays, data.getOverdueDays());
            }
            if (data.getTotalDebtRatio() != null && data.getTotalDebtRatio().compareTo(BigDecimal.ONE) < 0) {
                validTotalDebtRatio = validTotalDebtRatio.max(data.getTotalDebtRatio());
            }
            if (data.getIncomeReliability() != null && data.getIncomeReliability().compareTo(BigDecimal.ZERO) > 0) {
                validIncomeReliability = validIncomeReliability.add(data.getIncomeReliability());
            }
            if (data.getCourtExecutionCount() != null && data.getCourtExecutionCount() < Integer.MAX_VALUE) {
                validCourtExecutionCount += data.getCourtExecutionCount();
            }
            if (data.getCourtExecutionDetails() != null) {
                allCourtDetails.addAll(data.getCourtExecutionDetails());
            }
        }

        if (validCount > 0) {
            result.setMultiLendingCount(validMultiLendingCount);
            result.setOverdueDays(validOverdueDays);
            result.setTotalDebtRatio(validTotalDebtRatio);
            result.setIncomeReliability(validIncomeReliability.divide(
                    BigDecimal.valueOf(validCount), 2, RoundingMode.HALF_UP));
            result.setCourtExecutionCount(validCourtExecutionCount);
            result.setCourtExecutionDetails(allCourtDetails);

            if (hasFallback) {
                result.setQualityTag(DataQualityTag.PARTIAL);
            } else if (hasPartial) {
                result.setQualityTag(DataQualityTag.PARTIAL);
            } else {
                result.setQualityTag(DataQualityTag.NORMAL);
            }
        } else {
            result = fallbackService.getFallbackData(request, "ALL", null);
        }

        result.setDataSourceStatus(dataSourceStatus);

        log.info("[征信集成-聚合] 数据聚合完成, validSources: {}, qualityTag: {}, " +
                        "multiLendingCount: {}, overdueDays: {}, debtRatio: {}, incomeReliability: {}",
                validCount, result.getQualityTag(),
                result.getMultiLendingCount(), result.getOverdueDays(),
                result.getTotalDebtRatio(), result.getIncomeReliability());

        return result;
    }

    private CreditQueryResponse buildTimeoutResponse(CreditQueryRequest request, String queryId,
                                                     long totalStart,
                                                     Map<String, CreditQueryResponse.DataSourceResult> results) {
        long totalCost = System.currentTimeMillis() - totalStart;

        StructuredCreditData fallbackData = fallbackService.getFallbackData(request, "TIMEOUT", null);

        CreditQueryResponse response = new CreditQueryResponse();
        response.setRequestId(request.getRequestId());
        response.setQueryId(queryId);
        response.setSuccess(false);
        response.setMessage("查询超时，返回保守默认值，待人工复核");
        response.setData(fallbackData);
        response.setQualityTag(DataQualityTag.PENDING_REVIEW);
        response.setDataSourceResults(results);
        response.setTotalCostMs(totalCost);
        response.setQueryTime(LocalDateTime.now());
        response.setAsyncQuery(false);

        callLogService.saveQuerySummaryLog(queryId, request, response, creditCircuitBreakers);

        return response;
    }

    private CreditQueryResponse buildErrorResponse(CreditQueryRequest request, String queryId,
                                                   long totalStart, Exception e,
                                                   Map<String, CreditQueryResponse.DataSourceResult> results) {
        long totalCost = System.currentTimeMillis() - totalStart;

        StructuredCreditData fallbackData = fallbackService.getFallbackData(request, "ERROR", e);

        CreditQueryResponse response = new CreditQueryResponse();
        response.setRequestId(request.getRequestId());
        response.setQueryId(queryId);
        response.setSuccess(false);
        response.setMessage("查询异常: " + e.getMessage() + "，返回保守默认值，待人工复核");
        response.setData(fallbackData);
        response.setQualityTag(DataQualityTag.PENDING_REVIEW);
        response.setDataSourceResults(results);
        response.setTotalCostMs(totalCost);
        response.setQueryTime(LocalDateTime.now());
        response.setAsyncQuery(false);

        callLogService.saveQuerySummaryLog(queryId, request, response, creditCircuitBreakers);

        return response;
    }

    private List<String> getDataSourceCodes(List<CreditDataSourceType> dataSources) {
        if (dataSources == null) return Collections.emptyList();
        return dataSources.stream()
                .map(CreditDataSourceType::getCode)
                .collect(java.util.stream.Collectors.toList());
    }
}
