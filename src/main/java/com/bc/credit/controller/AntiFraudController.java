package com.bc.credit.controller;

import com.bc.credit.common.Result;
import com.bc.credit.dto.AntiFraudCheckResultDTO;
import com.bc.credit.engine.impl.DroolsRuleEngine;
import com.bc.credit.entity.AntiFraudResult;
import com.bc.credit.entity.AntiFraudRule;
import com.bc.credit.entity.FraudRuleABTest;
import com.bc.credit.entity.LoanApplication;
import com.bc.credit.mapper.AntiFraudResultMapper;
import com.bc.credit.mapper.AntiFraudRuleMapper;
import com.bc.credit.mapper.FraudRuleABTestMapper;
import com.bc.credit.service.AntiFraudService;
import com.bc.credit.service.impl.AntiFraudServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Api(tags = "反欺诈规则管理")
@RestController
@RequestMapping("/api/anti-fraud")
public class AntiFraudController {

    @Autowired
    private AntiFraudServiceImpl antiFraudServiceImpl;

    @Autowired
    private AntiFraudRuleMapper antiFraudRuleMapper;

    @Autowired
    private AntiFraudResultMapper antiFraudResultMapper;

    @Autowired
    private FraudRuleABTestMapper fraudRuleABTestMapper;

    @Autowired
    private DroolsRuleEngine droolsRuleEngine;

    @Value("${credit.anti-fraud.rule-engine:QL_EXPRESS}")
    private String activeRuleEngine;

    @PostMapping("/check")
    @ApiOperation("执行反欺诈校验")
    public Result<AntiFraudCheckResultDTO> checkFraud(
            @ApiParam("贷款申请") @RequestBody LoanApplication application,
            @ApiParam("设备信息") @RequestParam(required = false) String deviceInfo,
            @ApiParam("IP地址") @RequestParam(required = false) String ipAddress) {
        try {
            AntiFraudCheckResultDTO result = antiFraudServiceImpl.checkFraud(application, deviceInfo, ipAddress);
            return Result.success("反欺诈校验完成", result);
        } catch (Exception e) {
            log.error("反欺诈校验失败", e);
            return Result.error("反欺诈校验失败: " + e.getMessage());
        }
    }

    @GetMapping("/rules")
    @ApiOperation("查询反欺诈规则列表")
    public Result<IPage<AntiFraudRule>> getRules(
            @ApiParam("页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @ApiParam("规则类型") @RequestParam(required = false) String ruleType,
            @ApiParam("是否启用") @RequestParam(required = false) Integer enabled) {
        try {
            QueryWrapper<AntiFraudRule> wrapper = new QueryWrapper<>();
            wrapper.orderByAsc("sort_order");
            if (ruleType != null && !ruleType.isEmpty()) {
                wrapper.eq("rule_type", ruleType);
            }
            if (enabled != null) {
                wrapper.eq("enabled", enabled);
            }
            IPage<AntiFraudRule> page = antiFraudRuleMapper.selectPage(
                    new Page<>(pageNum, pageSize), wrapper);
            return Result.success(page);
        } catch (Exception e) {
            log.error("查询反欺诈规则列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/rules")
    @ApiOperation("新增反欺诈规则")
    public Result<AntiFraudRule> addRule(@RequestBody AntiFraudRule rule) {
        try {
            if (rule.getRuleCode() == null || rule.getRuleCode().isEmpty()) {
                return Result.error("规则编码不能为空");
            }
            if (rule.getRuleExpression() != null && !rule.getRuleExpression().isEmpty()) {
                boolean valid = antiFraudServiceImpl.validateRuleExpression(rule.getRuleExpression());
                if (!valid) {
                    return Result.error("规则表达式语法错误");
                }
            }
            rule.setCreatedTime(LocalDateTime.now());
            rule.setUpdatedTime(LocalDateTime.now());
            antiFraudRuleMapper.insert(rule);
            antiFraudServiceImpl.refreshRuleCache();
            return Result.success("规则新增成功", rule);
        } catch (Exception e) {
            log.error("新增反欺诈规则失败", e);
            return Result.error("新增失败: " + e.getMessage());
        }
    }

    @PutMapping("/rules/{id}")
    @ApiOperation("更新反欺诈规则")
    public Result<Void> updateRule(@PathVariable Long id, @RequestBody AntiFraudRule rule) {
        try {
            AntiFraudRule existing = antiFraudRuleMapper.selectById(id);
            if (existing == null) {
                return Result.error(404, "规则不存在");
            }
            if (rule.getRuleExpression() != null && !rule.getRuleExpression().isEmpty()) {
                boolean valid = antiFraudServiceImpl.validateRuleExpression(rule.getRuleExpression());
                if (!valid) {
                    return Result.error("规则表达式语法错误");
                }
            }
            rule.setId(id);
            rule.setUpdatedTime(LocalDateTime.now());
            antiFraudRuleMapper.updateById(rule);
            antiFraudServiceImpl.refreshRuleCache();
            return Result.success("规则更新成功", null);
        } catch (Exception e) {
            log.error("更新反欺诈规则失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @PutMapping("/rules/{id}/toggle")
    @ApiOperation("启用/禁用规则")
    public Result<Void> toggleRule(@PathVariable Long id) {
        try {
            AntiFraudRule rule = antiFraudRuleMapper.selectById(id);
            if (rule == null) {
                return Result.error(404, "规则不存在");
            }
            rule.setEnabled(rule.getEnabled() == 1 ? 0 : 1);
            rule.setUpdatedTime(LocalDateTime.now());
            antiFraudRuleMapper.updateById(rule);
            antiFraudServiceImpl.refreshRuleCache();
            return Result.success(rule.getEnabled() == 1 ? "规则已启用" : "规则已禁用", null);
        } catch (Exception e) {
            log.error("切换规则状态失败", e);
            return Result.error("操作失败: " + e.getMessage());
        }
    }

    @GetMapping("/results")
    @ApiOperation("查询反欺诈结果列表")
    public Result<IPage<AntiFraudResult>> getResults(
            @ApiParam("页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @ApiParam("客户ID") @RequestParam(required = false) String customerId,
            @ApiParam("检查结果") @RequestParam(required = false) Integer checkResult) {
        try {
            QueryWrapper<AntiFraudResult> wrapper = new QueryWrapper<>();
            wrapper.orderByDesc("check_time");
            if (customerId != null && !customerId.isEmpty()) {
                wrapper.eq("customer_id", customerId);
            }
            if (checkResult != null) {
                wrapper.eq("check_result", checkResult);
            }
            IPage<AntiFraudResult> page = antiFraudResultMapper.selectPage(
                    new Page<>(pageNum, pageSize), wrapper);
            return Result.success(page);
        } catch (Exception e) {
            log.error("查询反欺诈结果失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/drools/publish")
    @ApiOperation("发布DRL规则到Redis（热更新）")
    public Result<Void> publishDrl(
            @ApiParam("规则组") @RequestParam String group,
            @ApiParam("规则名称") @RequestParam String ruleName,
            @ApiParam("DRL内容") @RequestBody String drlContent) {
        try {
            boolean valid = antiFraudServiceImpl.validateDrl(drlContent);
            if (!valid) {
                return Result.error("DRL语法校验失败");
            }
            antiFraudServiceImpl.publishDrlToRedis(group, ruleName, drlContent);
            return Result.success("规则发布成功", null);
        } catch (Exception e) {
            log.error("发布DRL规则失败", e);
            return Result.error("发布失败: " + e.getMessage());
        }
    }

    @PostMapping("/drools/validate")
    @ApiOperation("校验DRL语法")
    public Result<Map<String, Object>> validateDrl(@RequestBody String drlContent) {
        try {
            boolean valid = antiFraudServiceImpl.validateDrl(drlContent);
            Map<String, Object> result = new HashMap<>();
            result.put("valid", valid);
            result.put("engineType", "DROOLS");
            return Result.success(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("valid", false);
            result.put("error", e.getMessage());
            return Result.success(result);
        }
    }

    @PostMapping("/drools/reload/{group}")
    @ApiOperation("重新加载指定规则组")
    public Result<Void> reloadRuleGroup(@PathVariable String group) {
        try {
            antiFraudServiceImpl.reloadDroolsRules(group);
            return Result.success("规则组重新加载成功", null);
        } catch (Exception e) {
            log.error("重新加载规则组失败, group: {}", group, e);
            return Result.error("重新加载失败: " + e.getMessage());
        }
    }

    @PostMapping("/drools/reload-all")
    @ApiOperation("重新加载所有规则")
    public Result<Void> reloadAllRules() {
        try {
            antiFraudServiceImpl.reloadAllDroolsRules();
            return Result.success("所有规则重新加载成功", null);
        } catch (Exception e) {
            log.error("重新加载所有规则失败", e);
            return Result.error("重新加载失败: " + e.getMessage());
        }
    }

    @GetMapping("/drools/status")
    @ApiOperation("获取规则组状态")
    public Result<Map<String, Object>> getRuleGroupStatus() {
        try {
            Map<String, Object> status = antiFraudServiceImpl.getRuleGroupStatus();
            status.put("activeEngine", activeRuleEngine);
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取规则组状态失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats/hit-rate")
    @ApiOperation("查询规则命中率统计")
    public Result<List<Map<String, Object>>> getRuleHitStats(
            @ApiParam("开始时间") @RequestParam(required = false) String startTime,
            @ApiParam("结束时间") @RequestParam(required = false) String endTime) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            if (startTime == null || startTime.isEmpty()) {
                startTime = LocalDateTime.now().minusDays(7).format(formatter);
            }
            if (endTime == null || endTime.isEmpty()) {
                endTime = LocalDateTime.now().format(formatter);
            }
            List<Map<String, Object>> stats = antiFraudServiceImpl.getRuleHitStats(startTime, endTime);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("查询规则命中率统计失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats/redis-hit/{ruleCode}")
    @ApiOperation("从Redis获取规则命中计数")
    public Result<Map<String, Object>> getRedisHitCount(@PathVariable String ruleCode) {
        try {
            long hitCount = droolsRuleEngine.getRuleHitCount(ruleCode);
            Map<String, Object> result = new HashMap<>();
            result.put("ruleCode", ruleCode);
            result.put("hitCount", hitCount);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取Redis命中计数失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    @PostMapping("/ab-test")
    @ApiOperation("创建A/B测试")
    public Result<FraudRuleABTest> createABTest(@RequestBody FraudRuleABTest abTest) {
        try {
            if (abTest.getGroupARuleContent() != null) {
                boolean validA = antiFraudServiceImpl.validateDrl(abTest.getGroupARuleContent());
                if (!validA) {
                    return Result.error("A组规则DRL语法校验失败");
                }
            }
            if (abTest.getGroupBRuleContent() != null) {
                boolean validB = antiFraudServiceImpl.validateDrl(abTest.getGroupBRuleContent());
                if (!validB) {
                    return Result.error("B组规则DRL语法校验失败");
                }
            }
            abTest.setCreatedTime(LocalDateTime.now());
            abTest.setUpdatedTime(LocalDateTime.now());
            abTest.setGroupASamples(0);
            abTest.setGroupBSamples(0);
            abTest.setGroupARejectCount(0);
            abTest.setGroupBRejectCount(0);
            abTest.setGroupAAlertCount(0);
            abTest.setGroupBAlertCount(0);
            abTest.setDeleted(0);
            fraudRuleABTestMapper.insert(abTest);

            if (abTest.getGroupARuleContent() != null) {
                antiFraudServiceImpl.publishDrlToRedis("A", abTest.getTestName() + "_A", abTest.getGroupARuleContent());
            }
            if (abTest.getGroupBRuleContent() != null) {
                antiFraudServiceImpl.publishDrlToRedis("B", abTest.getTestName() + "_B", abTest.getGroupBRuleContent());
            }

            return Result.success("A/B测试创建成功", abTest);
        } catch (Exception e) {
            log.error("创建A/B测试失败", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    @GetMapping("/ab-test/stats")
    @ApiOperation("查询A/B测试统计")
    public Result<List<Map<String, Object>>> getABTestStats(
            @ApiParam("开始时间") @RequestParam(required = false) String startTime,
            @ApiParam("结束时间") @RequestParam(required = false) String endTime) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            if (startTime == null || startTime.isEmpty()) {
                startTime = LocalDateTime.now().minusDays(7).format(formatter);
            }
            if (endTime == null || endTime.isEmpty()) {
                endTime = LocalDateTime.now().format(formatter);
            }
            List<Map<String, Object>> stats = antiFraudServiceImpl.getABTestStats(startTime, endTime);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("查询A/B测试统计失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PutMapping("/ab-test/{id}/status")
    @ApiOperation("更新A/B测试状态")
    public Result<Void> updateABTestStatus(
            @PathVariable Long id,
            @ApiParam("状态: CREATED/RUNNING/COMPLETED/STOPPED") @RequestParam String status) {
        try {
            FraudRuleABTest abTest = fraudRuleABTestMapper.selectById(id);
            if (abTest == null) {
                return Result.error(404, "A/B测试不存在");
            }
            abTest.setStatus(status);
            abTest.setUpdatedTime(LocalDateTime.now());
            fraudRuleABTestMapper.updateById(abTest);
            return Result.success("状态更新成功", null);
        } catch (Exception e) {
            log.error("更新A/B测试状态失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @PostMapping("/cache/refresh")
    @ApiOperation("刷新规则缓存")
    public Result<Void> refreshCache() {
        try {
            antiFraudServiceImpl.refreshRuleCache();
            return Result.success("缓存刷新成功", null);
        } catch (Exception e) {
            log.error("刷新规则缓存失败", e);
            return Result.error("刷新失败: " + e.getMessage());
        }
    }
}
