package com.vuong.simplerest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${app.openapi.title:Simple REST API}")
    private String title;

    @Value("${app.openapi.description:Auto-generated REST API for JPA entities}")
    private String description;

    @Value("${app.openapi.version:1.0.0}")
    private String version;

    @Value("${app.openapi.contact.name:Simple REST Team}")
    private String contactName;

    @Value("${app.openapi.contact.email:}")
    private String contactEmail;

    @Value("${app.openapi.license.name:MIT}")
    private String licenseName;

    @Value("${app.openapi.license.url:}")
    private String licenseUrl;

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)  // Prevents duplicate OpenAPI beans
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title(title)
                .description(description)
                .version(version)
                .contact(new Contact()
                    .name(contactName)
                    .email(contactEmail))
                .license(new License()
                    .name(licenseName)
                    .url(licenseUrl)))
            .addSecurityItem(new SecurityRequirement().addList("basicAuth"))
            .components(new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes("basicAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")));
    }
}
