package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ticket")
public class Ticket implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;
    private Long userId;
    private String title;
    private String category;
    private Integer priority;
    private Integer status;
    private Long assignedTo;
    private Long lastReplyUserId;
    private LocalDateTime lastReplyAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String userName;

    @TableField(exist = false)
    private String assigneeName;
}
