package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {
    
    @GetMapping("/test")
    public Result<?> test() {
        return Result.success("connect success!");
    }
}
