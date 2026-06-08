package com.bc.credit.common.enums;

import lombok.Getter;

@Getter
public enum DataQualityTag {

    NORMAL("NORMAL", "正常数据"),
    FALLBACK("FALLBACK", "降级默认值"),
    PENDING_REVIEW("PENDING_REVIEW", "待人工复核"),
    PARTIAL("PARTIAL", "部分数据源失败");

    private final String code;
    private final String name;

    DataQualityTag(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static DataQualityTag getByCode(String code) {
        for (DataQualityTag tag : values()) {
            if (tag.getCode().equals(code)) {
                return tag;
            }
        }
        return null;
    }
}
