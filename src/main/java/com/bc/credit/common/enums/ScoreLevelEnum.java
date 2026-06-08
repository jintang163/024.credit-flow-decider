package com.bc.credit.common.enums;

import lombok.Getter;

@Getter
public enum ScoreLevelEnum {

    A("A", "优秀", 800, 900),
    B("B", "良好", 700, 799),
    C("C", "一般", 600, 699),
    D("D", "较差", 500, 599),
    E("E", "很差", 300, 499);

    private final String code;
    private final String desc;
    private final Integer minScore;
    private final Integer maxScore;

    ScoreLevelEnum(String code, String desc, Integer minScore, Integer maxScore) {
        this.code = code;
        this.desc = desc;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public static ScoreLevelEnum getByScore(Integer score) {
        for (ScoreLevelEnum value : values()) {
            if (score >= value.getMinScore() && score <= value.getMaxScore()) {
                return value;
            }
        }
        return E;
    }
}
