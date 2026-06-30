package com.bryanstrk.pulser.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pulserOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pulser API")
                        .version("v1"));
    }
}
