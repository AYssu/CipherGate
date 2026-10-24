package com.ayssu.ciphergate.thirdparty.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ThirdPartyUserRateLimitServiceTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ThirdPartyUserRateLimitService limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class); values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        limiter = new ThirdPartyUserRateLimitService(redis);
    }

    @Test
    void freshCountersHaveTtlAndDoNotStoreRawIdentifiers() {
        when(values.increment(anyString())).thenReturn(1L);
        limiter.checkBeforeLogin(1L, "192.0.2.1", "alice@example.test");
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).expire(keys.capture(), eq(Duration.ofMinutes(2)));
        for (String key : keys.getAllValues()) {
            assertTrue(key.startsWith("cg:tp:user:rate:1:"));
            assertFalse(key.contains("alice")); assertFalse(key.contains("192.0.2.1"));
        }
    }

    @Test
    void ipLimitBlocksEvenWhenRotatingUsernames() {
        when(values.increment(anyString())).thenReturn(121L);
        assertEquals(429, assertThrows(ThirdPartyUserLoginException.class,
                () -> limiter.checkBeforeLogin(1L, "192.0.2.1", "alice")).getCode());
        verify(values, times(1)).increment(anyString());
    }

    @Test
    void accountPerIpLimitBlocksRepeatedAttempts() {
        when(values.increment(anyString())).thenReturn(5L, 21L);
        assertThrows(ThirdPartyUserLoginException.class,
                () -> limiter.checkBeforeLogin(1L, "192.0.2.1", "alice"));
    }

    @Test
    void redisFailureDoesNotSilentlyBypassRateLimit() {
        when(values.increment(anyString())).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class,
                () -> limiter.checkBeforeLogin(1L, "192.0.2.1", "alice"));
    }
}
