package com.douyin.service;

import com.douyin.common.CursorPageDTO;
import com.douyin.mapper.WatchHistoryMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** P1-07 contracts for stable keyset pagination and legacy compatibility. */
class WatchHistoryCursorContractTest {

    @Test
    void mapperUsesStableUpdateTimeAndIdBoundary() throws Exception {
        Method method = WatchHistoryMapper.class.getMethod("findHistoryCursor", Long.class,
                LocalDateTime.class, Long.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());
        assertTrue(sql.contains("h.update_time &lt;"));
        assertTrue(sql.contains("h.update_time ="));
        assertTrue(sql.contains("h.id &lt;"));
        assertTrue(sql.contains("ORDER BY h.update_time DESC, h.id DESC"));
        assertTrue(sql.contains("v.status = 'APPROVED'"));
        assertTrue(sql.contains("LIMIT #{limit}"));
    }

    @Test
    void filmTvCursorFiltersInDatabaseInsteadOfMemory() throws Exception {
        Method method = WatchHistoryMapper.class.getMethod("findHistoryOtherCursor", Long.class,
                java.util.Collection.class, LocalDateTime.class, Long.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());
        assertTrue(sql.contains("INNER JOIN t_video_content c"));
        assertTrue(sql.contains("c.text_category IN"));
        assertTrue(sql.contains("h.update_time &lt;"));
        assertTrue(sql.contains("ORDER BY h.update_time DESC, h.id DESC"));
    }

    @Test
    void cursorPageHasOpaqueCursorAndNoTotalOffsetContract() {
        CursorPageDTO<String> page = new CursorPageDTO<>(List.of("v"), "opaque", true);
        assertEquals(List.of("v"), page.getList());
        assertEquals("opaque", page.getNextCursor());
        assertTrue(page.isHasMore());
    }

    @Test
    void legacyPageServiceMethodRemainsPresent() throws Exception {
        assertNotNull(VideoService.class.getMethod("getHistory", Long.class, int.class, int.class));
        assertNotNull(VideoService.class.getMethod("getHistoryCursor", Long.class, String.class, int.class));
    }
}
