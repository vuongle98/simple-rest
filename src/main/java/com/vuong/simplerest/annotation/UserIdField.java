package com.vuong.simplerest.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the field name that represents the user ID in an entity.
 * This is used for security filtering to ensure users can only access their own data.
 * 
 * Default field name is "userId" if not specified.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserIdField {
    
    /**
     * The name of the field that stores the user ID.
     * Default is "userId".
     * 
     * @return the field name
     */
    String value() default "userId";
}
