package com.bc.credit.common.enums;

import lombok.Getter;

@Getter
public enum RiskLevelEnum {

    LOW("LOW", "低风险"),
    MEDIUM("MEDIUM", "中风险"),
    HIGH("HIGH", "高风险");

    private final String code;
    private final String desc;

    RiskLevelEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RiskLevelEnum getByCode(String code) {
        for (RiskLevelEnum value : values()) {
            if (value.getCode().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
