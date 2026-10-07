package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("access_event")
public class AccessEvent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventType;

    private Long appId;

    private Long refId;

    /** 应用隔离的设备摘要；历史记录和非设备事件可为空。 */
    private String deviceHash;

    private String clientIp;

    private LocalDateTime createdAt;
}
