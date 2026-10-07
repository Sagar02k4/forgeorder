package com.sagar.forgeorder.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI forgeOrderOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ForgeOrder API")
                        .description("Fault-Tolerant Order and Payment Orchestration Platform. " +
                                "Demonstrates idempotency, atomic inventory concurrency, transactional outbox, " +
                                "payment reconciliation, and dead-letter handling.")
                        .version("1.0")
                        .contact(new Contact()
                                .name("Sagar")
                                .url("https://github.com/Sagar02k4/forgeorder")));
    }
}