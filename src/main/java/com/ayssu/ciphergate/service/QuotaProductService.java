package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.QuotaProduct;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface QuotaProductService extends IService<QuotaProduct> {

    List<QuotaProduct> getActiveProducts();

    List<QuotaProduct> getProductsByType(String productType);
}
