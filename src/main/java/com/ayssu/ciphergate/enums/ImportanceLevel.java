package com.ayssu.ciphergate.enums;

/**
 * 消息重要程度枚举
 */
public enum ImportanceLevel {
    /**
     * 低 - 普通操作日志，不需要已读状态，自动视为已读
     */
    LOW("低", false, false, false),
    
    /**
     * 中 - 一般操作，需要已读状态，但不显示红点
     */
    MEDIUM("中", true, false, false),
    
    /**
     * 高 - 重要操作，需要已读状态并显示红点
     */
    HIGH("高", true, true, false),
    
    /**
     * 紧急 - 非常重要的操作，需要已读状态、显示红点并发送邮件通知
     */
    URGENT("紧急", true, true, true);
    
    private final String description;
    private final boolean needRead;   // 是否需要已读状态
    private final boolean showBadge;  // 是否显示红点
    private final boolean sendEmail;  // 是否发送邮件
    
    ImportanceLevel(String description, boolean needRead, boolean showBadge, boolean sendEmail) {
        this.description = description;
        this.needRead = needRead;
        this.showBadge = showBadge;
        this.sendEmail = sendEmail;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isNeedRead() {
        return needRead;
    }
    
    public boolean isShowBadge() {
        return showBadge;
    }
    
    public boolean isSendEmail() {
        return sendEmail;
    }
}
