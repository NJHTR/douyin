package com.douyin.kafka;

import com.douyin.entity.WatchHistory;
import com.douyin.entity.Video;
import com.douyin.kafka.dto.VideoEvent;
import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoCollectMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.mapper.WatchHistoryMapper;
import com.douyin.kafka.reliability.KafkaEventLedgerService;
import com.douyin.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 视频事件消费者 — 异步写入播放/点赞/收藏，解耦请求线程与 DB 写入。
 *
 * Kafka 启用时从 topic 消费；Kafka 未启用时由 VideoService 直写。
 * 消费失败向上抛出走重试/DLQ，成功后才记账+ack（幂等去重）。
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "douyin.kafka.enabled", havingValue = "true")
public class VideoEventConsumer {

    private final LikeMapper likeMapper;
    private final UserMapper userMapper;
    private final VideoMapper videoMapper;
    private final VideoCollectMapper collectMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final RedisCacheService cache;
    private final KafkaEventLedgerService ledger;

    public VideoEventConsumer(LikeMapper likeMapper, UserMapper userMapper, VideoMapper videoMapper,
                              VideoCollectMapper collectMapper,
                              WatchHistoryMapper watchHistoryMapper,
                              RedisCacheService cache,
                              KafkaEventLedgerService ledger) {
        this.likeMapper = likeMapper;
        this.userMapper = userMapper;
        this.videoMapper = videoMapper;
        this.collectMapper = collectMapper;
        this.watchHistoryMapper = watchHistoryMapper;
        this.cache = cache;
        this.ledger = ledger;
    }

    /** 消费视频事件 — 4 个并发消费者，对应 6 分区 */
    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_VIDEO_EVENTS,
            concurrency = "4",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onVideoEvent(VideoEvent event, Acknowledgment ack) {
        String topic = KafkaTopicConfig.TOPIC_VIDEO_EVENTS;
        if (ledger.isProcessed(topic, event.getEventId())) {
            ack.acknowledge();
            return;
        }
        try {
            handle(event);
            ledger.markProcessedOrThrow(topic, event.getEventId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Video event failed: action={} userId={} videoId={}",
                    event.getAction(), event.getUserId(), event.getVideoId(), e);
            throw new KafkaConsumeException("video", e);
        }
    }

    /** 也可被 DirectMessagePublisher 直接调用 (Kafka 未启用时) */
    public void handle(VideoEvent event) {
        switch (event.getAction()) {
            case "LIKE" -> handleLike(event);
            case "UNLIKE" -> handleUnlike(event);
            case "COLLECT" -> handleCollect(event);
            case "UNCOLLECT" -> handleUncollect(event);
            case "WATCH" -> handleWatch(event);
        }
    }

    private void handleLike(VideoEvent event) {
        Video video = requireVideo(event.getVideoId());
        if (likeMapper.insertIgnore(event.getUserId(), event.getVideoId()) == 0) return;
        requireCounterUpdate(videoMapper.incrementLike(event.getVideoId(), 1), event, "like");
        adjustAuthorFavorites(video, 1);
        cache.delete("douyin:video:meta:" + event.getVideoId());
        cache.invalidateRecommend(event.getUserId());
    }

    private void handleUnlike(VideoEvent event) {
        Video video = requireVideo(event.getVideoId());
        if (likeMapper.deleteByUserAndVideo(event.getUserId(), event.getVideoId()) == 0) return;
        requireCounterUpdate(videoMapper.incrementLike(event.getVideoId(), -1), event, "like");
        adjustAuthorFavorites(video, -1);
        cache.delete("douyin:video:meta:" + event.getVideoId());
    }

    private void handleCollect(VideoEvent event) {
        requireVideo(event.getVideoId());
        if (collectMapper.insertIgnore(event.getUserId(), event.getVideoId()) == 0) return;
        requireCounterUpdate(videoMapper.incrementCollect(event.getVideoId(), 1), event, "collect");
        cache.delete("douyin:video:meta:" + event.getVideoId());
    }

    private void handleUncollect(VideoEvent event) {
        requireVideo(event.getVideoId());
        if (collectMapper.deleteByUserAndVideo(event.getUserId(), event.getVideoId()) == 0) return;
        requireCounterUpdate(videoMapper.incrementCollect(event.getVideoId(), -1), event, "collect");
        cache.delete("douyin:video:meta:" + event.getVideoId());
    }

    private Video requireVideo(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new IllegalStateException("Video not found: " + videoId);
        }
        return video;
    }

    private void requireCounterUpdate(int rows, VideoEvent event, String counter) {
        if (rows != 1) {
            throw new IllegalStateException("Unable to update " + counter
                    + " counter for video " + event.getVideoId());
        }
    }

    private void adjustAuthorFavorites(Video video, int delta) {
        if (video.getAuthorUserId() != null && video.getAuthorUserId() > 0) {
            userMapper.incrementTotalFavorited(video.getAuthorUserId(), delta);
        }
    }

    private void handleWatch(VideoEvent event) {
        WatchHistory wh = new WatchHistory();
        wh.setUserId(event.getUserId());
        wh.setVideoId(event.getVideoId());
        wh.setWatchDuration(event.getWatchDuration());
        wh.setVideoDuration(event.getVideoDuration());
        wh.setFinished(event.getFinished() != null && event.getFinished() ? 1 : 0);
        wh.setSwipeSeconds(event.getSwipeSeconds());
        wh.setCreateTime(LocalDateTime.now());
        try {
            watchHistoryMapper.insert(wh);
            Video video = videoMapper.selectById(event.getVideoId());
            if (video != null) {
                video.setPlayCount((video.getPlayCount() != null ? video.getPlayCount() : 0) + 1);
                videoMapper.updateById(video);
            }
        } catch (Exception e) {
            log.warn("Watch history insert failed: userId={} videoId={}", event.getUserId(), event.getVideoId());
        }
    }
}
