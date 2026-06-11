package com.bc.credit.common.enums;

import lombok.Getter;

@Getter
public enum AsyncTaskStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败"),
    TIMEOUT(4, "超时"),
    MANUAL_REVIEW(5, "转人工"),
    DEAD_LETTER(6, "进入死信队列"),
    COMPENSATED(7, "已补偿"),
    RETRYING(8, "重试中"),
    CANCELLED(9, "已取消");

    private final Integer code;
    private final String desc;

    AsyncTaskStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AsyncTaskStatus getByCode(Integer code) {
        if (code == null) return null;
        for (AsyncTaskStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
