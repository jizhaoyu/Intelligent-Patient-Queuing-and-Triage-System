package com.hospital.triage.common.enums;

import lombok.Getter;

@Getter
public enum TriageLevelEnum {

    LEVEL_1(1, 1000),
    LEVEL_2(2, 700),
    LEVEL_3(3, 400),
    LEVEL_4(4, 100);

    private final int level;
    private final int weight;

    TriageLevelEnum(int level, int weight) {
        this.level = level;
        this.weight = weight;
    }

    public static TriageLevelEnum fromLevel(Integer level) {
        if (level == null) {
            return LEVEL_4;
        }
        for (TriageLevelEnum value : values()) {
            if (value.level == level) {
                return value;
            }
        }
        return LEVEL_4;
    }
}
