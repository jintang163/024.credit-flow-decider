package com.bc.credit.controller;

import com.bc.credit.common.Result;
import com.bc.credit.dto.LoanApplicationDTO;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.service.LoanApplicationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@Api(tags = "贷款申请管理")
@RestController
@RequestMapping("/api/application")
public class LoanApplicationController {

    @Autowired
    private LoanApplicationService loanApplicationService;

    @PostMapping("/submit")
    @ApiOperation("提交贷款申请")
    public Result<Map<String, Object>> submitApplication(
            @Validated @RequestBody LoanApplicationDTO applicationDTO) {
        log.info("收到贷款申请提交请求, customerId: {}, loanAmount: {}",
                applicationDTO.getCustomerId(), applicationDTO.getLoanAmount());
        try {
            Map<String, Object> result = loanApplicationService.submitApplication(applicationDTO);
            return Result.success("申请提交成功", result);
        } catch (Exception e) {
            log.error("贷款申请提交失败", e);
            return Result.error("申请提交失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @ApiOperation("根据ID查询申请详情")
    public Result<LoanApplication> getApplicationById(
            @ApiParam("申请ID") @PathVariable Long id) {
        try {
            LoanApplication application = loanApplicationService.getApplicationById(id);
            if (application == null) {
                return Result.error(404, "申请不存在");
            }
            return Result.success(application);
        } catch (Exception e) {
            log.error("查询申请详情失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/no/{applicationNo}")
    @ApiOperation("根据申请编号查询申请详情")
    public Result<LoanApplication> getApplicationByNo(
            @ApiParam("申请编号") @PathVariable String applicationNo) {
        try {
            LoanApplication application = loanApplicationService.getApplicationByNo(applicationNo);
            if (application == null) {
                return Result.error(404, "申请不存在");
            }
            return Result.success(application);
        } catch (Exception e) {
            log.error("查询申请详情失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/list")
    @ApiOperation("分页查询申请列表")
    public Result<Map<String, Object>> queryApplicationPage(
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页条数") @RequestParam(defaultValue = "10") int size,
            @RequestBody(required = false) Map<String, Object> params) {
        try {
            Map<String, Object> result = loanApplicationService.queryApplicationPage(page, size, params);
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询申请列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
}
