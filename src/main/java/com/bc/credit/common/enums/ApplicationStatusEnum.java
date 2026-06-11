package com.bc.credit.common.enums;

import lombok.Getter;

@Getter
public enum ApplicationStatusEnum {

    PENDING(0, "待审核"),
    APPROVING(1, "审批中"),
    APPROVED(2, "审批通过"),
    REJECTED(3, "审批拒绝"),
    WITHDRAWN(4, "已撤回"),
    SUPPLEMENTING(5, "补充资料中"),
    REVIEWING(6, "复核中"),
    RETURNED(7, "已退回"),
    MANUAL_REVIEW(8, "人工复核中"),
    ASYNC_PROCESSING(9, "异步处理中");

    private final Integer code;
    private final String desc;

    ApplicationStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ApplicationStatusEnum getByCode(Integer code) {
        for (ApplicationStatusEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
