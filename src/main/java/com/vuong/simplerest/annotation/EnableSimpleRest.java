package com.vuong.simplerest.annotation;


import org.springframework.context.annotation.Import;
import com.vuong.simplerest.config.AutoConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable Simple REST functionality in a Spring Boot application.
 * This imports the {@link com.vuong.simplerest.config.AutoConfig} class which sets up the necessary
 * configurations for automatic REST API generation based on JPA entities.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({AutoConfig.class})
public @interface EnableSimpleRest {
}
