package com.bc.credit.controller;

import com.bc.credit.common.Result;
import com.bc.credit.common.enums.CreditDataSourceType;
import com.bc.credit.common.enums.QueryMode;
import com.bc.credit.dto.credit.CreditQueryRequest;
import com.bc.credit.dto.credit.CreditQueryResponse;
import com.bc.credit.entity.CreditApiCallLog;
import com.bc.credit.integration.service.CreditIntegrationService;
import com.bc.credit.mapper.CreditApiCallLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Api(tags = "征信数据集成服务")
@RestController
@RequestMapping("/api/credit-integration")
public class CreditIntegrationController {

    @Autowired
    private CreditIntegrationService creditIntegrationService;

    @Autowired
    private CreditApiCallLogMapper creditApiCallLogMapper;

    @PostMapping("/query/sync")
    @ApiOperation("同步查询征信数据")
    public Result<CreditQueryResponse> queryCreditSync(
            @ApiParam("征信查询请求") @RequestBody CreditQueryRequest request) {

        log.info("[征信API] 同步查询请求, customerId: {}, dataSources: {}",
                request.getCustomerId(), request.getDataSources());

        try {
            if (request.getDataSources() == null || request.getDataSources().isEmpty()) {
                request.setDataSources(Arrays.asList(
                        CreditDataSourceType.PBOC,
                        CreditDataSourceType.BAIHANG,
                        CreditDataSourceType.SOCIAL_SECURITY,
                        CreditDataSourceType.HOUSING_FUND
                ));
            }

            request.setQueryMode(QueryMode.SYNC);
            CreditQueryResponse response = creditIntegrationService.queryCreditSync(request);

            log.info("[征信API] 同步查询完成, queryId: {}, success: {}, totalCost: {}ms",
                    response.getQueryId(), response.isSuccess(), response.getTotalCostMs());

            if (response.isAllSuccess()) {
                return Result.success("查询成功", response);
            } else {
                return Result.success("查询完成，部分数据源失败，数据已打标待人工复核", response);
            }

        } catch (Exception e) {
            log.error("[征信API] 同步查询异常", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/query/async")
    @ApiOperation("异步查询征信数据（MQ通知结果）")
    public Result<CreditQueryResponse> queryCreditAsync(
            @ApiParam("征信查询请求") @RequestBody CreditQueryRequest request) {

        log.info("[征信API] 异步查询请求, customerId: {}, callbackUrl: {}",
                request.getCustomerId(), request.getCallbackUrl());

        try {
            if (request.getDataSources() == null || request.getDataSources().isEmpty()) {
                request.setDataSources(Arrays.asList(
                        CreditDataSourceType.PBOC,
                        CreditDataSourceType.BAIHANG,
                        CreditDataSourceType.SOCIAL_SECURITY,
                        CreditDataSourceType.HOUSING_FUND
                ));
            }

            request.setQueryMode(QueryMode.ASYNC);
            CreditQueryResponse response = creditIntegrationService.queryCreditAsync(request);

            log.info("[征信API] 异步查询已提交, queryId: {}", response.getQueryId());

            return Result.success("异步查询已提交，结果将通过MQ通知", response);

        } catch (Exception e) {
            log.error("[征信API] 异步查询异常", e);
            return Result.error("提交失败: " + e.getMessage());
        }
    }

    @PostMapping("/query")
    @ApiOperation("智能查询征信数据（根据查询模式自动选择）")
    public Result<CreditQueryResponse> queryCredit(
            @ApiParam("征信查询请求") @RequestBody CreditQueryRequest request) {

        log.info("[征信API] 智能查询请求, customerId: {}, queryMode: {}",
                request.getCustomerId(),
                request.getQueryMode() != null ? request.getQueryMode().getCode() : "SYNC");

        try {
            if (request.getDataSources() == null || request.getDataSources().isEmpty()) {
                request.setDataSources(Arrays.asList(
                        CreditDataSourceType.PBOC,
                        CreditDataSourceType.BAIHANG
                ));
            }

            if (request.getQueryMode() == null) {
                request.setQueryMode(QueryMode.SYNC);
            }

            CreditQueryResponse response = creditIntegrationService.queryCredit(request);

            log.info("[征信API] 智能查询完成, queryId: {}, async: {}, success: {}",
                    response.getQueryId(), response.isAsyncQuery(), response.isSuccess());

            return Result.success(response.isAsyncQuery() ? "异步查询已提交" : "查询完成", response);

        } catch (Exception e) {
            log.error("[征信API] 智能查询异常", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/logs")
    @ApiOperation("分页查询征信调用日志")
    public Result<IPage<CreditApiCallLog>> getCallLogs(
            @ApiParam("页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @ApiParam("客户ID") @RequestParam(required = false) String customerId,
            @ApiParam("申请ID") @RequestParam(required = false) Long applicationId,
            @ApiParam("数据源") @RequestParam(required = false) String dataSource,
            @ApiParam("是否成功") @RequestParam(required = false) Integer success) {

        log.info("[征信API] 查询调用日志, pageNum: {}, pageSize: {}, customerId: {}, dataSource: {}",
                pageNum, pageSize, customerId, dataSource);

        try {
            QueryWrapper<CreditApiCallLog> wrapper = new QueryWrapper<>();
            wrapper.orderByDesc("call_time");

            if (customerId != null && !customerId.isEmpty()) {
                wrapper.eq("customer_id", customerId);
            }
            if (applicationId != null) {
                wrapper.eq("application_id", applicationId);
            }
            if (dataSource != null && !dataSource.isEmpty()) {
                wrapper.eq("data_source", dataSource);
            }
            if (success != null) {
                wrapper.eq("success", success);
            }

            IPage<CreditApiCallLog> page = creditApiCallLogMapper.selectPage(
                    new Page<>(pageNum, pageSize), wrapper);

            return Result.success(page);

        } catch (Exception e) {
            log.error("[征信API] 查询调用日志异常", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/logs/{queryId}")
    @ApiOperation("根据查询ID获取调用日志列表")
    public Result<List<CreditApiCallLog>> getCallLogsByQueryId(
            @ApiParam("查询ID") @PathVariable String queryId) {

        log.info("[征信API] 根据查询ID获取日志, queryId: {}", queryId);

        try {
            QueryWrapper<CreditApiCallLog> wrapper = new QueryWrapper<>();
            wrapper.eq("query_id", queryId);
            wrapper.orderByAsc("call_time");

            List<CreditApiCallLog> logs = creditApiCallLogMapper.selectList(wrapper);

            return Result.success(logs);

        } catch (Exception e) {
            log.error("[征信API] 查询调用日志异常", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/data-sources")
    @ApiOperation("获取可用的数据源列表")
    public Result<List<CreditDataSourceType>> getDataSources() {
        log.info("[征信API] 获取数据源列表");
        try {
            return Result.success(Arrays.asList(CreditDataSourceType.values()));
        } catch (Exception e) {
            log.error("[征信API] 获取数据源列表异常", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }
}
