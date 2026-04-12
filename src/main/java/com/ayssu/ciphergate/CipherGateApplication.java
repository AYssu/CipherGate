package com.ayssu.ciphergate;

import com.ayssu.ciphergate.thirdparty.config.ThirdPartyPublicProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.ayssu.ciphergate.mapper")
@EnableConfigurationProperties(ThirdPartyPublicProperties.class)
public class CipherGateApplication {

    public static void main(String[] args) {
        SpringApplication.run(CipherGateApplication.class, args);
    }

}
