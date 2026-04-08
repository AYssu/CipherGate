package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "连通性测试", description = "基础服务连通性检查接口")
public class TestController {
    
    @GetMapping("/test")
    @Operation(summary = "服务连通性测试")
    public Result<?> test() {
        return Result.success("connect success!");
    }
}
