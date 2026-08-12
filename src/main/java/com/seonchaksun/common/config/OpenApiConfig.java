package com.seonchaksun.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI seonchaksunOpenAPI() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title("선착순 처리 시스템 API")
                                .description("""
                                        높은 동시성 환경에서 선착순 신청을 처리하고
                                        Atomic Update, Pessimistic Lock,
                                        Optimistic Lock, Redis + MySQL 전략을
                                        비교하기 위한 API입니다.
                                        """)
                                .version("v1.0.0")
                );
    }
}