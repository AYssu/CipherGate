package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.MembershipLevel;
import com.ayssu.ciphergate.service.MembershipLevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/membership/levels")
@RequiredArgsConstructor
@Tag(name = "会员等级管理", description = "超级管理员管理会员等级配置")
public class MembershipLevelController {

    private final MembershipLevelService membershipLevelService;

    @GetMapping
    @RequirePermission("MEMBERSHIP_LEVEL_LIST")
    @Operation(summary = "获取会员等级列表")
    public Result<List<MembershipLevel>> getLevels() {
        return Result.success(membershipLevelService.getAllLevels());
    }

    @GetMapping("/{id}")
    @RequirePermission("MEMBERSHIP_LEVEL_LIST")
    @Operation(summary = "获取会员等级详情")
    public Result<MembershipLevel> getLevelById(@PathVariable Long id) {
        MembershipLevel level = membershipLevelService.getById(id);
        if (level == null) {
            return Result.error("等级不存在");
        }
        return Result.success(level);
    }

    @PostMapping
    @RequirePermission("MEMBERSHIP_LEVEL_CREATE")
    @Operation(summary = "创建会员等级")
    public Result<String> createLevel(@RequestBody MembershipLevel level) {
        membershipLevelService.save(level);
        return Result.success("创建成功");
    }

    @PutMapping("/{id}")
    @RequirePermission("MEMBERSHIP_LEVEL_UPDATE")
    @Operation(summary = "更新会员等级")
    public Result<String> updateLevel(@PathVariable Long id, @RequestBody MembershipLevel level) {
        level.setId(id);
        membershipLevelService.updateById(level);
        return Result.success("更新成功");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("MEMBERSHIP_LEVEL_DELETE")
    @Operation(summary = "删除会员等级")
    public Result<String> deleteLevel(@PathVariable Long id) {
        membershipLevelService.removeById(id);
        return Result.success("删除成功");
    }
}
