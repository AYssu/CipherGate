package com.ayssu.ciphergate.dto.transfer;

/** 持久化的幂等批次审计；不存放密码、邮箱、手机号或应用密钥。overwriteCount 记录复用目标行的总数（覆盖及恢复）。 */
public record AppUserTransferBatch(String requestId, Long operatorId, Long sourceAppId, Long targetAppId,
        String mode, boolean forceOverwrite, int createCount, int overwriteCount, int skipCount, String selectedIds) {}
