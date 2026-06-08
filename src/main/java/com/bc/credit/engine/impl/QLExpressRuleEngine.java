package com.bc.credit.engine.impl;

import com.bc.credit.engine.RuleEngine;
import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import com.ql.util.express.IExpressContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.Map;

@Slf4j
@Component("qlExpressRuleEngine")
public class QLExpressRuleEngine implements RuleEngine {

    @Value("${credit.anti-fraud.cache-rules:true}")
    private boolean cacheEnabled;

    @Value("${credit.anti-fraud.cache-expire-minutes:30}")
    private int cacheExpireMinutes;

    private ExpressRunner runner;

    @PostConstruct
    public void init() {
        runner = new ExpressRunner(true, false);
        runner.addOperatorWithAlias("&&", "并且", null);
        runner.addOperatorWithAlias("||", "或者", null);
        runner.addOperatorWithAlias("!", "非", null);
        runner.addOperatorWithAlias(">", "大于", null);
        runner.addOperatorWithAlias("<", "小于", null);
        runner.addOperatorWithAlias(">=", "大于等于", null);
        runner.addOperatorWithAlias("<=", "小于等于", null);
        runner.addOperatorWithAlias("==", "等于", null);
        runner.addOperatorWithAlias("!=", "不等于", null);
        runner.addOperatorWithAlias("+", "加", null);
        runner.addOperatorWithAlias("-", "减", null);
        runner.addOperatorWithAlias("*", "乘", null);
        runner.addOperatorWithAlias("/", "除", null);
        runner.addFunctionOfClassMethod("in", QLExpressRuleEngine.class.getName(), "in",
                new Class[]{Object.class, Object[].class}, null);
        runner.addFunctionOfClassMethod("contains", QLExpressRuleEngine.class.getName(), "contains",
                new Class[]{String.class, String.class}, null);
        runner.addFunctionOfClassMethod("matches", QLExpressRuleEngine.class.getName(), "matches",
                new Class[]{String.class, String.class}, null);
        runner.addFunctionOfClassMethod("isNull", QLExpressRuleEngine.class.getName(), "isNull",
                new Class[]{Object.class}, null);
        runner.addFunctionOfClassMethod("isNotNull", QLExpressRuleEngine.class.getName(), "isNotNull",
                new Class[]{Object.class}, null);

        log.info("QLExpress 规则引擎初始化完成, cacheEnabled: {}", cacheEnabled);
    }

    @Override
    public Object execute(String expression, Map<String, Object> context) throws Exception {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        IExpressContext<String, Object> expressContext = new DefaultContext<>();
        if (context != null) {
            expressContext.putAll(context);
        }

        try {
            Object result = runner.execute(expression, expressContext, null, true, false);
            log.debug("规则执行成功, expression: {}, result: {}", expression, result);
            return result;
        } catch (Exception e) {
            log.error("规则执行失败, expression: {}, context: {}", expression, context, e);
            throw new Exception("规则执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Boolean executeBoolean(String expression, Map<String, Object> context) throws Exception {
        Object result = execute(expression, context);
        if (result == null) {
            return false;
        }
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result instanceof Number) {
            return ((Number) result).intValue() != 0;
        }
        return Boolean.parseBoolean(result.toString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(String expression, Map<String, Object> context, Class<T> resultType) throws Exception {
        Object result = execute(expression, context);
        if (result == null) {
            return null;
        }
        return (T) result;
    }

    @Override
    public boolean validateExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        try {
            runner.parseInstructionSet(expression);
            return true;
        } catch (Exception e) {
            log.warn("规则表达式校验失败: {}", expression, e);
            return false;
        }
    }

    public static boolean in(Object obj, Object... array) {
        if (obj == null || array == null) {
            return false;
        }
        for (Object item : array) {
            if (obj.equals(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        return str.contains(searchStr);
    }

    public static boolean matches(String str, String regex) {
        if (str == null || regex == null) {
            return false;
        }
        return str.matches(regex);
    }

    public static boolean isNull(Object obj) {
        return obj == null;
    }

    public static boolean isNotNull(Object obj) {
        return obj != null;
    }
}
