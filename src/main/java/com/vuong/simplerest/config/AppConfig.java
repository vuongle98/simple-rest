package com.vuong.simplerest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    
    private String apiPrefix;
    
    public String getApiPrefix() {
        return apiPrefix;
    }
    
    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }
}
