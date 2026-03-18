package com.ayssu.ciphergate;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.ayssu.ciphergate.mapper")
public class CipherGateApplication {

    public static void main(String[] args) {
        SpringApplication.run(CipherGateApplication.class, args);
    }

}
