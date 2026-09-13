package com.douyin.websocket;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.repository.RtcAclMapper;
import com.douyin.rtc.repository.RtcCallParticipantMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WebSocketAuthorizationServiceTest {

    @Test
    void directMessageRequiresExistingOtherUser() {
        RtcAclMapper acl = mock(RtcAclMapper.class);
        when(acl.userExists(99L)).thenReturn(false);
        WebSocketAuthorizationService service =
                new WebSocketAuthorizationService(acl, mock(RtcCallParticipantMapper.class));

        assertThrows(CallDomainException.class,
                () -> service.assertDirectMessageAllowed(1L, 99L));
    }

    @Test
    void groupMessageRequiresMembership() {
        RtcAclMapper acl = mock(RtcAclMapper.class);
        when(acl.isGroupMember(7L, 1L)).thenReturn(false);
        WebSocketAuthorizationService service =
                new WebSocketAuthorizationService(acl, mock(RtcCallParticipantMapper.class));

        assertThrows(CallDomainException.class,
                () -> service.assertGroupMember(1L, 7L));
    }

    @Test
    void callSignalRequiresParticipantAndTargets() {
        RtcCallParticipantMapper participants = mock(RtcCallParticipantMapper.class);
        when(participants.findByCallAndUser("call-1", 1L)).thenReturn(null);
        WebSocketAuthorizationService service =
                new WebSocketAuthorizationService(mock(RtcAclMapper.class), participants);

        assertThrows(CallDomainException.class,
                () -> service.assertCallParticipant(1L, "call-1"));
    }
}
