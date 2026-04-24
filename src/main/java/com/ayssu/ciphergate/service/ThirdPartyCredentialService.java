package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.ThirdPartyCredentialDTO;
import com.ayssu.ciphergate.dto.ThirdPartyCredentialQueryDTO;
import com.ayssu.ciphergate.dto.ThirdPartyRechargeLogQueryDTO;
import com.ayssu.ciphergate.entity.ThirdPartyCredential;
import com.ayssu.ciphergate.entity.ThirdPartyRechargeLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface ThirdPartyCredentialService {
    Page<ThirdPartyCredential> pageCredentials(ThirdPartyCredentialQueryDTO queryDTO, Long operatorId);

    ThirdPartyCredential getCredential(Long id, Long operatorId);

    ThirdPartyCredential createCredential(ThirdPartyCredentialDTO dto, Long operatorId);

    ThirdPartyCredential updateCredential(Long id, ThirdPartyCredentialDTO dto, Long operatorId);

    void deleteCredential(Long id, Long operatorId);

    ThirdPartyCredential rotateSecret(Long id, Long operatorId);

    Page<ThirdPartyRechargeLog> pageRechargeLogs(ThirdPartyRechargeLogQueryDTO queryDTO, Long operatorId);
}
