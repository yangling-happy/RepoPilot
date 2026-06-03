package com.repopilot.common.util;

import com.repopilot.common.exception.BusinessException;
import org.springframework.util.StringUtils;


//Biz是Business的简称，所以这个类是用来抛出业务异常的工具类
public final class BizAssert {

    //私有构造方法，因为这个类只提供 static 工具方法，不需要创建对象
    private BizAssert() {
    }

    //判断 condition 是否为 true
    public static void isTrue(boolean condition, Integer code, String message) {
        if (!condition) {
            throw new BusinessException(code, message);
        }
    }

    //判断字符串是否有内容
    public static void hasText(String value, Integer code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(code, message);
        }
    }

    //判断对象是否不为 null
    public static void notNull(Object value, Integer code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
    }

    //判断数据库影响行数是否等于 1
    public static void affectedOne(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new BusinessException(500, message);
        }
    }
}
