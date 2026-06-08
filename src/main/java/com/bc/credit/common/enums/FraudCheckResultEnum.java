package com.bc.credit.common.enums;

import lombok.Getter;

@Getter
public enum FraudCheckResultEnum {

    PASS(0, "通过"),
    ALERT(1, "需人工复核"),
    REJECT(2, "拒绝");

    private final Integer code;
    private final String desc;

    FraudCheckResultEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static FraudCheckResultEnum getByCode(Integer code) {
        for (FraudCheckResultEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
