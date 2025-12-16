package com.vuong.simplerest.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Simple REST module.
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class SimpleRestProperties {

    private String[] basePackages = { "com.vuong.core.module" };
    
    /**
     * Security configuration for user-based data filtering.
     */
    private Security security = new Security();
    
    @Getter
    @Setter
    public static class Security {
        /**
         * Enable user-based security filtering.
         * When true, all data operations will be filtered by current user ID.
         */
        private boolean enabled = true;
        
        /**
         * Default user ID field name for entities.
         * Can be overridden per entity using @UserIdField annotation.
         */
        private String defaultUserIdField = "userId";
        
        /**
         * Skip security filtering for these entity classes.
         * Specify fully qualified class names.
         */
        private String[] skipEntities = {};
        
        /**
         * Fail fast when security validation fails.
         * When true, will throw exceptions immediately.
         * When false, will log warnings and continue.
         */
        private boolean failFast = true;
    }
}
