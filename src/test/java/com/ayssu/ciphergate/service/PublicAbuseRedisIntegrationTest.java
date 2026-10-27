package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.PublicChallengeRequest;
import com.ayssu.ciphergate.util.LoginRateLimiter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses CI's isolated Redis; unique identities avoid touching any unrelated keys. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named="CIPHERGATE_BOOT_JDBC_URL", matches=".+/ciphergate_boot_test.*")
class PublicAbuseRedisIntegrationTest {
    static LettuceConnectionFactory connection; static StringRedisTemplate redis;
    PublicAbuseGuard guard; AppUserRegisterOtpRedisService otp; String email;
    @BeforeAll static void connect() {
        connection=new LettuceConnectionFactory(System.getenv().getOrDefault("CIPHERGATE_TEST_REDIS_HOST","127.0.0.1"), Integer.parseInt(System.getenv().getOrDefault("CIPHERGATE_TEST_REDIS_PORT","6379")));connection.afterPropertiesSet();connection.start();
        redis=new StringRedisTemplate(connection);redis.afterPropertiesSet();
    }
    @AfterAll static void close() { connection.destroy(); }
    @BeforeEach void setup() { guard=new PublicAbuseGuard(redis);otp=new AppUserRegisterOtpRedisService(redis);email=UUID.randomUUID()+"@example.test"; }
    List<Boolean> concurrent(java.util.function.Supplier<Boolean> action) throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        try { var tasks=new ArrayList<Callable<Boolean>>();for(int i=0;i<16;i++)tasks.add(action::get);
            var result=new ArrayList<Boolean>();for(var future:pool.invokeAll(tasks))result.add(future.get());return result;
        } finally {pool.shutdownNow();}
    }
    @Test void sendReservationIsAtomicAcrossServiceInstances() throws Exception {
        var other=new AppUserRegisterOtpRedisService(redis);
        assertEquals(1,concurrent(() -> (Thread.currentThread().getId()%2==0?otp:other).reserveSend(1L,email)).stream().filter(Boolean::booleanValue).count());
    }
    @Test void successfulOtpCanBeConsumedOnlyOnceAndFailuresInvalidateIt() throws Exception {
        otp.saveOtp(1L,email,"123456");
        assertEquals(1,concurrent(() -> otp.matchesOtp(1L,email,"123456")).stream().filter(Boolean::booleanValue).count());
        otp.saveOtp(1L,email,"123456");for(int i=0;i<5;i++)assertFalse(otp.matchesOtp(1L,email,"wrong"));
        assertFalse(otp.matchesOtp(1L,email,"123456"));
        otp.saveOtp(1L,email,"654321");assertTrue(otp.matchesOtp(1L,email,"654321"));
    }
    @Test void fixedWindowIsAtomicExpiringAndShared() throws Exception {
        String identity=UUID.randomUUID().toString();
        assertEquals(5,concurrent(() -> {try {new PublicAbuseGuard(redis).check("integration",identity,5,Duration.ofSeconds(60));return true;}
            catch(ResponseStatusException e){assertEquals(429,e.getStatusCode().value());return false;}}).stream().filter(Boolean::booleanValue).count());
        Long ttl=redis.getExpire("abuse:integration:"+PublicAbuseGuard.digest(identity));assertNotNull(ttl);assertTrue(ttl>0 && ttl<=60);
    }
    @Test void challengesRejectReplayWrongPurposeAndDifferentIp() {
        var challenges=new PublicChallengeService(redis,guard);String ip="test-"+UUID.randomUUID();
        var issued=challenges.issue("license-query",ip);String key="public:challenge:"+issued.get("captchaId");
        String value=redis.opsForValue().get(key);assertNotNull(value);
        var body=new PublicChallengeRequest();body.setCaptchaId(issued.get("captchaId"));body.setCaptchaCode(value.substring(value.lastIndexOf(':')+1));
        assertThrows(ResponseStatusException.class,()->challenges.verify("license-unbind",ip,email,body));
        assertThrows(ResponseStatusException.class,()->challenges.verify("license-query",ip,email,body));
        issued=challenges.issue("license-query",ip);body.setCaptchaId(issued.get("captchaId"));value=redis.opsForValue().get("public:challenge:"+body.getCaptchaId());
        body.setCaptchaCode(value.substring(value.lastIndexOf(':')+1));
        assertThrows(ResponseStatusException.class,()->challenges.verify("license-query","other-ip",email,body));
        issued=challenges.issue("license-query",ip);body.setCaptchaId(issued.get("captchaId"));value=redis.opsForValue().get("public:challenge:"+body.getCaptchaId());
        body.setCaptchaCode(value.substring(value.lastIndexOf(':')+1));challenges.verify("license-query",ip,email,body);
        assertThrows(ResponseStatusException.class,()->challenges.verify("license-query",ip,email,body));
    }
    @Test void adminFailuresSurviveLimiterRecreationAndNormalizeIdentity() {
        var one=new LoginRateLimiter(redis);String identity="account:"+email;
        for(int i=0;i<5;i++)one.recordFailure(identity);
        var two=new LoginRateLimiter(redis);assertTrue(two.getLockRemainingSeconds(identity.toUpperCase(Locale.ROOT))>0);
        two.clearFailures(identity);assertEquals(0,one.getLockRemainingSeconds(identity));
    }
}
