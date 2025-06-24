package org.vuong.simplerest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vuong.simplerest.core.projection.ProjectionDefinition;
import org.vuong.simplerest.core.projection.ProjectionHandler;

@Configuration
public class AutoConfig {

    @Bean
    public ProjectionHandler projectionHandler() {
        return new ProjectionHandler();
    }

    @Bean
    public Class<?>[] projectionInterfaces() {
        return new Class<?>[]{ProjectionDefinition.class};
    }
}
