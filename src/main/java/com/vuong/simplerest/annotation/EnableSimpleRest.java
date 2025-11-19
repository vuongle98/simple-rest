package com.vuong.simplerest.annotation;


import org.springframework.context.annotation.Import;
import com.vuong.simplerest.config.AutoConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({AutoConfig.class})
public @interface EnableSimpleRest {
}
