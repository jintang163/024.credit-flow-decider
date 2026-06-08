package com.bc.credit.common.enums;

import lombok.Getter;

@Getter
public enum QueryMode {

    SYNC("SYNC", "同步查询"),
    ASYNC("ASYNC", "异步回调");

    private final String code;
    private final String name;

    QueryMode(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static QueryMode getByCode(String code) {
        for (QueryMode mode : values()) {
            if (mode.getCode().equals(code)) {
                return mode;
            }
        }
        return null;
    }
}
