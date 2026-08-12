package com.codey.web.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字典注解
 * @author fay
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Dict {

    /** 字典code */
    String dicCode() default "";

    /** 字典字段 */
    String dicColumn() default "";

    /** 数据字典表 */
    String dicTable() default "";

    /** 结果属性字段，翻译结果强制赋值到指定字段，不指定默认_dictText后缀字段，其次Desc后缀字段 */
    String resultField() default "";

}
