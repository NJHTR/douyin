package com.douyin.websocket;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.repository.RtcAclMapper;
import com.douyin.rtc.repository.RtcCallParticipantMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Central authorization policy for messages and realtime signaling.
 * Handlers must not trust recipient/group/call identifiers supplied by a client.
 */
@Service
public class WebSocketAuthorizationService {

    private final RtcAclMapper aclMapper;
    private final RtcCallParticipantMapper participantMapper;

    public WebSocketAuthorizationService(RtcAclMapper aclMapper,
                                         RtcCallParticipantMapper participantMapper) {
        this.aclMapper = aclMapper;
        this.participantMapper = participantMapper;
    }

    public void assertDirectMessageAllowed(Long actorId, Long targetUserId) {
        if (actorId == null || targetUserId == null || actorId.equals(targetUserId)
                || targetUserId <= 0 || !aclMapper.userExists(targetUserId)) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED,
                    "无权向该用户发送消息");
        }
    }

    public void assertGroupMember(Long actorId, Long groupId) {
        if (actorId == null || groupId == null || groupId <= 0
                || !aclMapper.isGroupMember(groupId, actorId)) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED,
                    "不是群成员,无权发送或订阅群消息");
        }
    }

    public void assertCallParticipant(Long actorId, String callId) {
        if (actorId == null || callId == null || callId.isBlank()
                || participantMapper.findByCallAndUser(callId, actorId) == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED,
                    "不是通话参与者,无权发送通话信令");
        }
    }

    public void assertCallTargets(Collection<Long> actorTargets, String callId) {
        if (actorTargets == null || actorTargets.isEmpty()) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT,
                    "缺少通话目标");
        }
        for (Long targetId : actorTargets) {
            if (targetId == null || participantMapper.findByCallAndUser(callId, targetId) == null) {
                throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED,
                        "通话目标不是该通话参与者");
            }
        }
    }
}
