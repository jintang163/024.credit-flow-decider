package com.bc.credit.engine;

import java.util.Map;

public interface RuleEngine {

    Object execute(String expression, Map<String, Object> context) throws Exception;

    Boolean executeBoolean(String expression, Map<String, Object> context) throws Exception;

    <T> T execute(String expression, Map<String, Object> context, Class<T> resultType) throws Exception;

    boolean validateExpression(String expression);
}
