package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.BalanceTransaction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface BalanceTransactionService extends IService<BalanceTransaction> {

    Page<BalanceTransaction> getUserTransactions(Long userId, int page, int size);
}
