package com.douluodalu.game.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("斗罗大陆·放置传说 API")
                .description("网页游戏后端接口文档")
                .version("1.0.0")
        )
        .addSecurityItem(SecurityRequirement().addList("Bearer"))
        .components(
            Components()
                .addSecuritySchemes(
                    "Bearer",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT Token 认证")
                )
        )
}
