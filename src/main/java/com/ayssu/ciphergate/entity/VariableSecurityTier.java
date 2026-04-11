package com.ayssu.ciphergate.entity;

/**
 * 应用变量安全分级（用于 WS 下发语义；高等级变量客户端应在 TEE 内解密与使用）。
 * <ul>
 *   <li>{@link #STANDARD} — 一般配置</li>
 *   <li>{@link #SENSITIVE} — 敏感，建议仅内存短时持有</li>
 *   <li>{@link #CRITICAL} — 关键；协议上仍经 WS 密文传输，客户端须仅在 TEE 内处理</li>
 * </ul>
 */
public final class VariableSecurityTier {
    public static final int STANDARD = 0;
    public static final int SENSITIVE = 1;
    public static final int CRITICAL = 2;

    private VariableSecurityTier() {}

    /** 与 {@link #STANDARD} 等一致的 JSON 桶名（HEARTBEAT 明文结构内）。 */
    public static String bucketName(int code) {
        int c = code < STANDARD ? STANDARD : (code > CRITICAL ? CRITICAL : code);
        return switch (c) {
            case SENSITIVE -> "SENSITIVE";
            case CRITICAL -> "CRITICAL";
            default -> "STANDARD";
        };
    }

    public static int normalize(Integer tier) {
        if (tier == null) {
            return CRITICAL;
        }
        if (tier < STANDARD) {
            return STANDARD;
        }
        if (tier > CRITICAL) {
            return CRITICAL;
        }
        return tier;
    }
}
