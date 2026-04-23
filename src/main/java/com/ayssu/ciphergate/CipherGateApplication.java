package com.ayssu.ciphergate;

import com.ayssu.ciphergate.thirdparty.config.ThirdPartyPublicProperties;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@SpringBootApplication
@MapperScan("com.ayssu.ciphergate.mapper")
@EnableConfigurationProperties(ThirdPartyPublicProperties.class)
@Slf4j
public class CipherGateApplication {

    public static void main(String[] args) {
        // 打印系统时间 格式化
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		log.info("当前系统时间：{}", now.format(formatter));
        SpringApplication.run(CipherGateApplication.class, args);
    }

}
