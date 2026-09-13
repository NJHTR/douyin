package com.douyin.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Stable keyset/cursor page. The cursor is opaque to API consumers. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CursorPageDTO<T> {
    private List<T> list;
    private String nextCursor;
    private boolean hasMore;
}
