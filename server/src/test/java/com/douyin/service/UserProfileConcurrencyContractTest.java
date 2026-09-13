package com.douyin.service;

import com.douyin.entity.UserContentProfile;
import com.douyin.entity.VideoContent;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.SearchHistoryMapper;
import com.douyin.mapper.UserContentProfileMapper;
import com.douyin.mapper.VideoCollectMapper;
import com.douyin.mapper.VideoContentMapper;
import com.douyin.mapper.VideoExposureMapper;
import com.douyin.mapper.VisitorMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Locks the cross-instance profile update contract at the mapper boundary. */
class UserProfileConcurrencyContractTest {

    @Test
    void incrementalUpdateCreatesAndLocksTheProfileRow() {
        UserContentProfileMapper profiles = mock(UserContentProfileMapper.class);
        UserContentProfile profile = new UserContentProfile();
        profile.setUserId(11L);
        profile.setTotalLikeCount(0);
        profile.setTotalInteractCount(0);
        when(profiles.selectForUpdate(11L)).thenReturn(profile);

        UserProfileService service = new UserProfileService(
                profiles,
                mock(com.douyin.mapper.WatchHistoryMapper.class),
                mock(VideoExposureMapper.class),
                mock(LikeMapper.class),
                mock(VideoCollectMapper.class),
                mock(FollowMapper.class),
                mock(SearchHistoryMapper.class),
                mock(VisitorMapper.class),
                mock(VideoContentMapper.class),
                mock(VideoTagService.class));

        service.onLike(11L, 42L, 7L);

        verify(profiles).insertIfAbsent(11L);
        verify(profiles).selectForUpdate(11L);
        verify(profiles).insertOrUpdate(profile);
    }

    @Test
    void incrementalProjectionEntryPointsAreTransactional() throws Exception {
        Method method = UserProfileService.class.getMethod("onWatch", Long.class, Long.class,
                Long.class, double.class, double.class, String.class, String.class, double.class);
        if (!method.isAnnotationPresent(Transactional.class)) {
            throw new AssertionError("onWatch must keep the row lock until persistence completes");
        }
    }

    @Test
    void quickSwipeIsNegativeEvidenceAndDoesNotSeedContentPreference() {
        UserContentProfile profile = new UserContentProfile();
        profile.setUserId(11L);
        profile.setTotalViewCount(0);
        profile.setTotalWatchCount(0);
        profile.setAvgWatchDuration(0.0);
        profile.setAvgCompletionRate(0.0);
        profile.setBounceRate(0.0);
        profile.setTotalWatchTimeSec(0L);

        UserContentProfileMapper profiles = mock(UserContentProfileMapper.class);
        when(profiles.selectForUpdate(11L)).thenReturn(profile);
        VideoContent content = new VideoContent();
        content.setVideoId(42L);
        content.setContentVector("[1.0,0.0]");
        content.setTextCategory("food");
        VideoContentMapper contents = mock(VideoContentMapper.class);
        when(contents.selectById(42L)).thenReturn(content);

        UserProfileService service = new UserProfileService(
                profiles,
                mock(com.douyin.mapper.WatchHistoryMapper.class),
                mock(VideoExposureMapper.class),
                mock(LikeMapper.class),
                mock(VideoCollectMapper.class),
                mock(FollowMapper.class),
                mock(SearchHistoryMapper.class),
                mock(VisitorMapper.class),
                contents,
                mock(VideoTagService.class));

        service.onWatch(11L, 42L, 7L, 1.0, 30.0,
                "HOME_RECOMMEND", "session-a", 1.0);

        org.junit.jupiter.api.Assertions.assertNull(profile.getContentVector());
        org.junit.jupiter.api.Assertions.assertNull(profile.getShortTermVector());
        org.junit.jupiter.api.Assertions.assertNull(profile.getCategoryWeights());
        org.junit.jupiter.api.Assertions.assertTrue(profile.getCreatorAffinity() != null,
                "quick swipe should remain available as creator-level negative evidence");
    }
}
