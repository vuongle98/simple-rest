package com.vuong.simplerest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import com.vuong.simplerest.core.projection.ProjectionDefinition;
import com.vuong.simplerest.core.projection.ProjectionHandler;

/**
 * Auto-configuration class for the Simple REST module.
 * This configuration enables component scanning for the module's packages
 * and provides core beans such as the ProjectionHandler.
 */
@Configuration
@ComponentScan(basePackages = "com.vuong.simplerest")
public class AutoConfig {

    /**
     * Creates a ProjectionHandler bean for handling entity projections.
     * @return a new ProjectionHandler instance
     */
    @Bean
    public ProjectionHandler projectionHandler() {
        return new ProjectionHandler();
    }

    /**
     * Provides an array of projection interface classes used by the module.
     * @return an array containing the ProjectionDefinition class
     */
    @Bean
    public Class<?>[] projectionInterfaces() {
        return new Class<?>[]{ProjectionDefinition.class};
    }
}
