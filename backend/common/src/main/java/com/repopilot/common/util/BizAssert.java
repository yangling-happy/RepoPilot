package com.repopilot.common.util;

import com.repopilot.common.exception.BusinessException;
import org.springframework.util.StringUtils;

public final class BizAssert {

    private BizAssert() {
    }

    public static void isTrue(boolean condition, Integer code, String message) {
        if (!condition) {
            throw new BusinessException(code, message);
        }
    }

    public static void hasText(String value, Integer code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(code, message);
        }
    }

    public static void notNull(Object value, Integer code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
    }

    public static void affectedOne(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new BusinessException(500, message);
        }
    }
}
