package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify Redis configuration works correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisConfigTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void testRedisTemplateIsConfigured() {
        assertThat(redisTemplate).isNotNull();
    }

    @Test
    void testRedisConnection() {
        // Test basic Redis operations
        String key = "test:key";
        String value = "test:value";
        
        redisTemplate.opsForValue().set(key, value);
        Object retrieved = redisTemplate.opsForValue().get(key);
        
        assertThat(retrieved).isEqualTo(value);
        
        // Cleanup
        redisTemplate.delete(key);
    }
}
