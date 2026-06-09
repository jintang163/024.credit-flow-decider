package com.bc.credit.controller;

import com.bc.credit.common.Result;
import com.bc.credit.dto.CreditScoreDTO;
import com.bc.credit.entity.CreditScoreResult;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.CreditScoreResultMapper;
import com.bc.credit.mapper.LoanApplicationMapper;
import com.bc.credit.service.CreditScoringService;
import com.bc.credit.service.impl.CreditScoringServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Api(tags = "信用评分管理")
@RestController
@RequestMapping("/api/scoring")
public class CreditScoringController {

    @Autowired
    private CreditScoringServiceImpl creditScoringServiceImpl;

    @Autowired
    private CreditScoringService creditScoringService;

    @Autowired
    private LoanApplicationMapper loanApplicationMapper;

    @Autowired
    private CreditScoreResultMapper creditScoreResultMapper;

    @PostMapping("/calculate")
    @ApiOperation("执行信用评分计算")
    public Result<CreditScoreDTO> calculateScore(
            @ApiParam("贷款申请ID") @RequestParam Long applicationId,
            @ApiParam("逾期次数") @RequestParam(required = false) Integer overdueCount,
            @ApiParam("剩余贷款金额") @RequestParam(required = false) BigDecimal remainingLoanAmount,
            @ApiParam("额外信息") @RequestBody(required = false) Map<String, Object> extraInfo) {
        try {
            LoanApplication application = loanApplicationMapper.selectById(applicationId);
            if (application == null) {
                return Result.error(404, "贷款申请不存在");
            }
            CreditScoreDTO result = creditScoringService.calculateScore(
                    application, application.getCreditScore(), overdueCount, remainingLoanAmount, extraInfo);
            return Result.success("评分计算完成", result);
        } catch (Exception e) {
            log.error("信用评分计算失败", e);
            return Result.error("评分计算失败: " + e.getMessage());
        }
    }

    @GetMapping("/results")
    @ApiOperation("查询评分结果列表")
    public Result<IPage<CreditScoreResult>> getResults(
            @ApiParam("页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @ApiParam("客户ID") @RequestParam(required = false) String customerId,
            @ApiParam("评分等级") @RequestParam(required = false) String scoreLevel) {
        try {
            QueryWrapper<CreditScoreResult> wrapper = new QueryWrapper<>();
            wrapper.orderByDesc("score_time");
            if (customerId != null && !customerId.isEmpty()) {
                wrapper.eq("customer_id", customerId);
            }
            if (scoreLevel != null && !scoreLevel.isEmpty()) {
                wrapper.eq("score_level", scoreLevel);
            }
            IPage<CreditScoreResult> page = creditScoreResultMapper.selectPage(
                    new Page<>(pageNum, pageSize), wrapper);
            return Result.success(page);
        } catch (Exception e) {
            log.error("查询评分结果失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/engine/status")
    @ApiOperation("获取评分引擎状态")
    public Result<Map<String, Object>> getEngineStatus() {
        try {
            Map<String, Object> status = creditScoringServiceImpl.getScoringEngineStatus();
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取评分引擎状态失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @PostMapping("/pmml/reload")
    @ApiOperation("重新加载PMML模型")
    public Result<Void> reloadPmmlModel() {
        try {
            creditScoringServiceImpl.reloadPmmlModel();
            return Result.success("PMML模型重新加载成功", null);
        } catch (Exception e) {
            log.error("PMML模型重新加载失败", e);
            return Result.error("重新加载失败: " + e.getMessage());
        }
    }

    @PostMapping("/pmml/switch/{version}")
    @ApiOperation("切换PMML模型版本")
    public Result<Void> switchPmmlModel(@PathVariable String version) {
        try {
            creditScoringServiceImpl.switchPmmlModel(version);
            return Result.success("PMML模型切换成功, version: " + version, null);
        } catch (Exception e) {
            log.error("PMML模型切换失败, version: {}", version, e);
            return Result.error("切换失败: " + e.getMessage());
        }
    }

    @PostMapping("/pmml/upload/{version}")
    @ApiOperation("上传PMML模型文件")
    public Result<Void> uploadPmmlModel(
            @PathVariable String version,
            @RequestBody byte[] pmmlContent) {
        try {
            boolean valid = creditScoringServiceImpl.validatePmmlModel(pmmlContent);
            if (!valid) {
                return Result.error("PMML模型文件校验失败");
            }
            creditScoringServiceImpl.uploadPmmlModel(version, pmmlContent);
            return Result.success("PMML模型上传成功", null);
        } catch (Exception e) {
            log.error("PMML模型上传失败, version: {}", version, e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/pmml/validate")
    @ApiOperation("校验PMML模型文件")
    public Result<Map<String, Object>> validatePmmlModel(@RequestBody byte[] pmmlContent) {
        try {
            boolean valid = creditScoringServiceImpl.validatePmmlModel(pmmlContent);
            Map<String, Object> result = new HashMap<>();
            result.put("valid", valid);
            return Result.success(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("valid", false);
            result.put("error", e.getMessage());
            return Result.success(result);
        }
    }
}
