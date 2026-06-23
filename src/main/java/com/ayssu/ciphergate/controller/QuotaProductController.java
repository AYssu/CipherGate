package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.QuotaProduct;
import com.ayssu.ciphergate.service.QuotaProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/quota-products")
@RequiredArgsConstructor
@Tag(name = "额度商品管理", description = "超级管理员管理额度商品")
public class QuotaProductController {

    private final QuotaProductService quotaProductService;

    @GetMapping
    @RequirePermission("QUOTA_PRODUCT_LIST")
    @Operation(summary = "获取额度商品列表")
    public Result<List<QuotaProduct>> getProducts() {
        return Result.success(quotaProductService.getActiveProducts());
    }

    /**
     * 获取额度商品列表（登录用户可用，用于购买）
     */
    @GetMapping("/public")
    @Operation(summary = "获取额度商品列表（登录用户可用）")
    public Result<List<QuotaProduct>> getProductsPublic() {
        return Result.success(quotaProductService.getActiveProducts());
    }

    @GetMapping("/{id}")
    @RequirePermission("QUOTA_PRODUCT_LIST")
    @Operation(summary = "获取额度商品详情")
    public Result<QuotaProduct> getProductById(@PathVariable Long id) {
        QuotaProduct product = quotaProductService.getById(id);
        if (product == null) {
            return Result.error("商品不存在");
        }
        return Result.success(product);
    }

    @PostMapping
    @RequirePermission("QUOTA_PRODUCT_CREATE")
    @Operation(summary = "创建额度商品")
    public Result<String> createProduct(@RequestBody QuotaProduct product) {
        quotaProductService.save(product);
        return Result.success("创建成功");
    }

    @PutMapping("/{id}")
    @RequirePermission("QUOTA_PRODUCT_UPDATE")
    @Operation(summary = "更新额度商品")
    public Result<String> updateProduct(@PathVariable Long id, @RequestBody QuotaProduct product) {
        product.setId(id);
        quotaProductService.updateById(product);
        return Result.success("更新成功");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("QUOTA_PRODUCT_DELETE")
    @Operation(summary = "删除额度商品")
    public Result<String> deleteProduct(@PathVariable Long id) {
        quotaProductService.removeById(id);
        return Result.success("删除成功");
    }
}
