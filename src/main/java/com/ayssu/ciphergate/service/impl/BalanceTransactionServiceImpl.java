package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.BalanceTransaction;
import com.ayssu.ciphergate.mapper.BalanceTransactionMapper;
import com.ayssu.ciphergate.service.BalanceTransactionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BalanceTransactionServiceImpl extends ServiceImpl<BalanceTransactionMapper, BalanceTransaction> implements BalanceTransactionService {

    @Override
    public Page<BalanceTransaction> getUserTransactions(Long userId, int page, int size) {
        return lambdaQuery()
                .eq(BalanceTransaction::getUserId, userId)
                .orderByDesc(BalanceTransaction::getCreatedAt)
                .page(new Page<>(page, size));
    }
}
