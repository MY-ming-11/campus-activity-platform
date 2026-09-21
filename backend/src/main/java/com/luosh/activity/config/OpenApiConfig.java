package com.luosh.activity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 配置。
 *
 * 关键作用是声明 bearer 安全方案——不声明的话页面右上角不会出现 Authorize 按钮，
 * 所有需要登录的接口在 Swagger 上都只能返回 401，没法在线试。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    private static final String DESCRIPTION =
            "先调 POST /api/auth/login 拿 token（这个接口不需要登录），"
            + "再点右上角 Authorize 把 token 粘进去，之后就能直接试需要登录的接口。"
            + "粘贴时不用带 Bearer 前缀，Swagger 会自己加。";

    @Bean
    public OpenAPI activityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("校园活动报名与签到平台")
                        .version("1.0.0")
                        .description(DESCRIPTION))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
