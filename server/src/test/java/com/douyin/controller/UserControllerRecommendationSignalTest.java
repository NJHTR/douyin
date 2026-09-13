package com.douyin.controller;

import com.douyin.kafka.MessagePublisher;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.FriendMapper;
import com.douyin.service.ContentFeatureService;
import com.douyin.service.NotificationDispatchService;
import com.douyin.service.SystemNoticeService;
import com.douyin.service.UserService;
import com.douyin.service.VideoService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerRecommendationSignalTest {

    @Test
    void unfollowDoesNotEmitPositiveProfileEvidence() {
        UserService users = mock(UserService.class);
        JwtUtil jwt = mock(JwtUtil.class);
        ContentFeatureService features = mock(ContentFeatureService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwt.getUserIdFromToken("token")).thenReturn(11L);
        when(users.toggleFollow(11L, 22L)).thenReturn(false);

        controller(users, jwt, features).toggleFollow(22L, request);

        verify(features, never()).onFollow(11L, 22L);
    }

    @Test
    void followEmitsPositiveProfileEvidence() {
        UserService users = mock(UserService.class);
        JwtUtil jwt = mock(JwtUtil.class);
        ContentFeatureService features = mock(ContentFeatureService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwt.getUserIdFromToken("token")).thenReturn(11L);
        when(users.toggleFollow(11L, 22L)).thenReturn(true);

        controller(users, jwt, features).toggleFollow(22L, request);

        verify(features).onFollow(11L, 22L);
    }

    private static UserController controller(UserService users, JwtUtil jwt,
                                             ContentFeatureService features) {
        return new UserController(
                users,
                mock(VideoService.class),
                jwt,
                mock(FollowMapper.class),
                mock(FriendMapper.class),
                mock(MessagePublisher.class),
                features,
                mock(SystemNoticeService.class),
                mock(NotificationDispatchService.class));
    }
}
