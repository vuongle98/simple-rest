package com.vuong.simplerest.core.projection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to define a projection configuration.
 * Used to mark classes that represent projection definitions for entity data.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectionDefinition {
    /**
     * The name of the projection.
     * @return the projection name
     */
    String name() default "";

    /**
     * The entity types this projection applies to.
     * @return array of entity classes
     */
    Class<?>[] types() default {};
} 