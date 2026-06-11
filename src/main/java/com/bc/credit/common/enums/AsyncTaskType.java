package com.bc.credit.common.enums;

import lombok.Getter;

@Getter
public enum AsyncTaskType {

    CREDIT_QUERY("CREDIT_QUERY", "征信查询"),
    CREDIT_SCORING("CREDIT_SCORING", "信用评分"),
    ANTI_FRAUD("ANTI_FRAUD", "反欺诈校验"),
    LIMIT_CALCULATION("LIMIT_CALCULATION", "额度计算");

    private final String code;
    private final String desc;

    AsyncTaskType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AsyncTaskType getByCode(String code) {
        if (code == null) return null;
        for (AsyncTaskType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
