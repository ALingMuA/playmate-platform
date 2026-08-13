package com.gameplay.user.domain;

/**
 * 性别枚举（对应 `user.gender`：0未知，1男，2女）。
 *
 * <p>需求 FR-A05：用户可维护性别等非敏感资料。</p>
 */
public enum Gender {

    UNKNOWN(0),
    MALE(1),
    FEMALE(2);

    private final int value;

    Gender(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /** 是否合法的性别取值 */
    public static boolean isValid(Integer value) {
        if (value == null) {
            return false;
        }
        for (Gender gender : values()) {
            if (gender.value == value) {
                return true;
            }
        }
        return false;
    }
}
