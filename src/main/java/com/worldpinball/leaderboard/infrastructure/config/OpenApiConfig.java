package com.worldpinball.leaderboard.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WorldPinball Leaderboard API")
                        .version("v1.0")
                        .description("REST API for WorldPinball Leaderboard, Game Sessions, Matchmaking, Machine Provisioning and Telemetry.")
                        .contact(new Contact().name("WorldPinball Team"))
                        .license(new License().name("Apache 2.0")));
    }
}
