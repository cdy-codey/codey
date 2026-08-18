package com.codey.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记字段不参与表单模式序列化。
 * 加在实体字段上时，该字段在表单 Schema 生成时会被忽略，不输出到 AI 填写结构中。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FormIgnore {
}
