package com.seonchaksun.entry.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class RedisCapacityService {

    private static final long SUCCESS = 1L;

    private static final String KEY_PREFIX =
            "event:capacity:";

    private final StringRedisTemplate
            stringRedisTemplate;

    private final DefaultRedisScript<Long>
            reserveCapacityScript;

    private final DefaultRedisScript<Long>
            releaseCapacityScript;

    public RedisCapacityService(
            StringRedisTemplate stringRedisTemplate,

            @Qualifier("reserveCapacityScript")
            DefaultRedisScript<Long>
                    reserveCapacityScript,

            @Qualifier("releaseCapacityScript")
            DefaultRedisScript<Long>
                    releaseCapacityScript
    ) {
        this.stringRedisTemplate =
                stringRedisTemplate;

        this.reserveCapacityScript =
                reserveCapacityScript;

        this.releaseCapacityScript =
                releaseCapacityScript;
    }

    public boolean reserve(
            Long eventId,
            int capacity
    ) {

        Long result =
                stringRedisTemplate.execute(
                        reserveCapacityScript,
                        Collections.singletonList(
                                createKey(eventId)
                        ),
                        String.valueOf(capacity)
                );

        return result != null
                && result == SUCCESS;
    }

    public boolean release(
            Long eventId
    ) {

        Long result =
                stringRedisTemplate.execute(
                        releaseCapacityScript,
                        Collections.singletonList(
                                createKey(eventId)
                        )
                );

        return result != null
                && result == SUCCESS;
    }

    public long getCurrentCount(
            Long eventId
    ) {

        String value =
                stringRedisTemplate
                        .opsForValue()
                        .get(
                                createKey(eventId)
                        );

        if (value == null) {
            return 0L;
        }

        return Long.parseLong(value);
    }

    public void clear(
            Long eventId
    ) {

        stringRedisTemplate.delete(
                createKey(eventId)
        );
    }

    private String createKey(
            Long eventId
    ) {

        return KEY_PREFIX
                + eventId;
    }
}