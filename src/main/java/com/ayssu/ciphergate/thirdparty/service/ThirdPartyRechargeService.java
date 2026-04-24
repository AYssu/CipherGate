package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.thirdparty.dto.ThirdPartyRechargeDTO;

public interface ThirdPartyRechargeService {
    Result<?> recharge(ThirdPartyRechargeDTO dto, String ipAddress, String userAgent);
}
