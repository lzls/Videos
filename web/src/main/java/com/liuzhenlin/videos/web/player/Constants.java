package com.liuzhenlin.videos.web.player;

import com.liuzhenlin.common.utils.NonNullApi;

@NonNullApi
public final class Constants {
    private Constants() {
    }

    public static final int UNKNOWN = -1;

    public static final String URL_BLANK = "about:blank";

    public static final class LinkType {
        private LinkType() {
        }

        public static final int SINGLES = 0;
        public static final int PLAYLIST = 1;
    }

    public static final class RepeatMode {
        private RepeatMode() {
        }

        public static final int NONE = 0;
        public static final int ALL = 1;
        public static final int SINGLE = 2;
    }

    public static final class Actions {
        private Actions() {
        }

        public static final String PLAY = "play";
        public static final String PAUSE = "pause";
        public static final String PLAY_PAUSE = "play_pause";
        public static final String PREV = "prev";
        public static final String NEXT = "next";
        public static final String START = "start";
        public static final String START_FOREGROUND = "startForeground";
        public static final String STOP_FOREGROUND = "stopForeground";
        public static final String STOP_SELF = "stopSelf";
    }

    public static final class Extras {
        private Extras() {
        }

        public static final String VIDEO_ID = "videoId";
        public static final String PLAYLIST_ID = "playlistId";
        public static final String VIDEO_INDEX = "videoIndex";
    }
}
