package com.douyin.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/** One row per direct-message conversation, assembled by MessageMapper. */
@Data
public class ConversationSummaryRow {

    private Long targetUserId;
    private String lastMessage;
    private Integer lastMsgType;
    private LocalDateTime lastTime;
    private Long unreadCount;
    private Long lastMsgFromUserId;
    private Integer lastMsgIsRead;
}
