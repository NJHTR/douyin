package com.douyin.service.impl;

import com.douyin.mapper.ConversationSummaryRow;
import com.douyin.mapper.MessageMapper;
import com.douyin.mapper.NotificationMapper;
import com.douyin.service.UserService;
import com.douyin.vo.ConversationVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceImplConversationTest {

    @Test
    void loadsConversationRowsWithOneBatchSummaryQuery() {
        MessageMapper mapper = mock(MessageMapper.class);
        UserService userService = mock(UserService.class);
        MessageServiceImpl service = new MessageServiceImpl(mock(NotificationMapper.class), userService);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        ConversationSummaryRow row = new ConversationSummaryRow();
        row.setTargetUserId(22L);
        row.setLastMessage("hello");
        row.setLastMsgType(1);
        row.setLastTime(LocalDateTime.of(2026, 8, 22, 12, 0));
        row.setUnreadCount(3L);
        row.setLastMsgFromUserId(22L);
        row.setLastMsgIsRead(0);
        when(mapper.selectConversationSummaries(7L, 200)).thenReturn(List.of(row));

        List<ConversationVO> conversations = service.getConversations(7L);

        assertEquals(1, conversations.size());
        assertEquals("hello", conversations.get(0).getLastMessage());
        assertEquals(3L, conversations.get(0).getUnreadCount());
        assertEquals(22L, conversations.get(0).getLastMsgFromUserId());
        verify(mapper).selectConversationSummaries(7L, 200);
    }
}
