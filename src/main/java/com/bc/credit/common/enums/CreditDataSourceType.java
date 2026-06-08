package com.bc.credit.common.enums;

import lombok.Getter;

@Getter
public enum CreditDataSourceType {

    PBOC("PBOC", "央行征信", true),
    BAIHANG("BAIHANG", "百行征信", true),
    SOCIAL_SECURITY("SOCIAL_SECURITY", "社保数据", false),
    HOUSING_FUND("HOUSING_FUND", "公积金数据", false);

    private final String code;
    private final String name;
    private final boolean isCreditBureau;

    CreditDataSourceType(String code, String name, boolean isCreditBureau) {
        this.code = code;
        this.name = name;
        this.isCreditBureau = isCreditBureau;
    }

    public static CreditDataSourceType getByCode(String code) {
        for (CreditDataSourceType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
