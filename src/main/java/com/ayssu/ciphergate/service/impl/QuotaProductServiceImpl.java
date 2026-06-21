package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.QuotaProduct;
import com.ayssu.ciphergate.mapper.QuotaProductMapper;
import com.ayssu.ciphergate.service.QuotaProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class QuotaProductServiceImpl extends ServiceImpl<QuotaProductMapper, QuotaProduct> implements QuotaProductService {

    @Override
    public List<QuotaProduct> getActiveProducts() {
        return lambdaQuery()
                .eq(QuotaProduct::getStatus, 1)
                .orderByAsc(QuotaProduct::getSortOrder)
                .list();
    }

    @Override
    public List<QuotaProduct> getProductsByType(String productType) {
        return lambdaQuery()
                .eq(QuotaProduct::getStatus, 1)
                .eq(QuotaProduct::getProductType, productType)
                .orderByAsc(QuotaProduct::getSortOrder)
                .list();
    }
}
