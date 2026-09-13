package com.douyin.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feed mode is a public compatibility boundary.  Keep aliases and the
 * fallback deterministic so older clients cannot accidentally select a
 * different ranking strategy after a deploy.
 */
class FeedChannelContractTest {

    @Test
    void parserAcceptsTheWireAliases() {
        assertEquals(FeedChannel.HOME, FeedChannel.parse(null));
        assertEquals(FeedChannel.HOME, FeedChannel.parse(""));
        assertEquals(FeedChannel.HOT, FeedChannel.parse("hot"));
        assertEquals(FeedChannel.LONG_VIDEO, FeedChannel.parse("long-video"));
        assertEquals(FeedChannel.FOLLOWING, FeedChannel.parse(" following "));
        assertEquals(FeedChannel.EXPERIENCE, FeedChannel.parse("experience"));
    }

    @Test
    void unknownModesFailClosedToHome() {
        assertEquals(FeedChannel.HOME, FeedChannel.parse("not-a-channel"));
        assertEquals(FeedChannel.HOME, FeedChannel.parse("home\t"));
    }

    @Test
    void channelClassificationDrivesStrategyBoundaries() {
        assertTrue(FeedChannel.HOME.isPersonalized());
        assertTrue(FeedChannel.EXPERIENCE.isPersonalized());
        assertTrue(FeedChannel.LONG_VIDEO.isPersonalized());
        assertTrue(FeedChannel.HOT.isGlobal());
        assertFalse(FeedChannel.HOT.isPersonalized());
        assertFalse(FeedChannel.FOLLOWING.isGlobal());
    }
}
