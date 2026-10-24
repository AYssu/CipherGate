package com.ayssu.ciphergate.dto.transfer;

import java.time.LocalDateTime;
import java.util.List;

public record AppUserTransferResult(
        String sourceAppName, String targetAppName, String previewToken,
        int createCount, int overwriteCount, int restoreCount, int skipCount, int blockCount,
        boolean executed, List<Item> items) {
    public enum Action { CREATE, OVERWRITE, RESTORE, SKIP, BLOCK }
    public record Item(Long sourceUserId, String username, Long targetUserId, String targetUsername,
                       Action action, String reason, int deviceCount, int bannedDeviceCount,
                       boolean trialRecorded) {}
    public record ApplicationOption(Long id, String appName, Long ownerId) {}
    public record UserRow(Long id, String username, String email, String nickname, LocalDateTime memberExpiresAt) {}
}
