package com.seonchaksun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {

    @Bean
    public DefaultRedisScript<Long>
    reserveCapacityScript() {

        DefaultRedisScript<Long> script =
                new DefaultRedisScript<>();

        script.setLocation(
                new ClassPathResource(
                        "redis/reserve-capacity.lua"
                )
        );

        script.setResultType(Long.class);

        return script;
    }

    @Bean
    public DefaultRedisScript<Long>
    releaseCapacityScript() {

        DefaultRedisScript<Long> script =
                new DefaultRedisScript<>();

        script.setLocation(
                new ClassPathResource(
                        "redis/release-capacity.lua"
                )
        );

        script.setResultType(Long.class);

        return script;
    }
}