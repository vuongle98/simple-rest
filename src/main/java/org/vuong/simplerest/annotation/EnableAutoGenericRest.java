package org.vuong.simplerest.annotation;


import org.springframework.context.annotation.Import;
import org.vuong.simplerest.config.LiteRestAutoConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(LiteRestAutoConfiguration.class)
public @interface EnableAutoGenericRest {
}
