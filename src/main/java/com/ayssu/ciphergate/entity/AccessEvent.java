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

    private LocalDateTime createdAt;
}
