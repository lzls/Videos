/*
 * Created on 2022-2-18 4:40:56 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.common.utils.Utils;

@NonNullApi
public final class Youtube {

    private Youtube() {}

    /*package*/ static final String REGEX_PROTOCOL = "http(s)?://";
    /*package*/ static final String REGEX_WATCH_URL_HOST = "(m|www)\\.youtube\\.com";
    /*package*/ static final String REGEX_SHARE_URL_HOST = "youtu\\.be";
    public static final String REGEX_WATCH_URL =
            "^(" + REGEX_PROTOCOL + REGEX_WATCH_URL_HOST + "/watch\\?).+";
    public static final String REGEX_SHARE_URL =
            "^" + REGEX_PROTOCOL + REGEX_SHARE_URL_HOST + "/((\\?list=)?[A-Za-z0-9_-]+)+.*";

    // For mobile terminal
    public static final class URLs {
        private URLs() {}

        public static final String HOME = "https://m.youtube.com";

        public static final String PLAYER_API = HOME + "/player_api";
    }

    public static final class PlayingStatus {
        private PlayingStatus() {
        }

        public static final int UNSTARTED = -1; // When the player first loads a video
        public static final int ENDED = 0;
        public static final int PLAYING = 1;
        public static final int PAUSED = 2;
        public static final int BUFFERRING = 3;
        public static final int VIDEO_CUED = 5; // When a video is cued and ready to play
    }

    public static final class IFrameJsInterface {
        private IFrameJsInterface() {}

        public static String loadVideo(String vId) {
            return "javascript:player.loadVideoById(\"" + vId + "\");";
        }

        public static String playVideo() {
            return "javascript:player.playVideo();";
        }

        public static String pauseVideo() {
            return "javascript:player.pauseVideo();";
        }

        public static String stopVideo() {
            return "javascript:player.stopVideo();";
        }

        public static String nextVideo() {
            return "javascript:player.nextVideo();";
        }

        public static String prevVideo() {
            return "javascript:player.previousVideo();";
        }

        public static String seekToDefault() {
            return seekTo(0);
        }

        public static String seekTo(long position) {
            return "javascript:"
                    + "player.seekTo(" + Utils.roundDouble(position / 1000d) + ", true);";
        }

        public static String fastRewind() {
            return "javascript:player.seekTo(player.getCurrentTime() + Number(-15), true);";
        }

        public static String fastForward() {
            return "javascript:player.seekTo(player.getCurrentTime() + Number(15), true);";
        }

        public static String setPlaybackQuality(String quality) {
            // The getPlaybackQuality function is no longer supported.
            // In particular, calls to setPlaybackQuality will be no-op functions,
            // meaning they will not actually have any impact on the viewer's playback experience.
            return "javascript:player.setPlaybackQuality(\"" + quality + "\");";
        }

        public static String loadPlaylist(String pId, int index) {
            return "javascript:player.loadPlaylist({list:\"" + pId + "\", index:" + index + "});";
        }

        public static String setLoopPlaylist(boolean loop) {
            return "javascript:player.setLoop(" + loop + ");";
        }

        public static String replayPlaylist() {
            return playVideoAt(0);
        }

        public static String playVideoAt(int index) {
            return "javascript:player.playVideoAt(" + index + ");";
        }

        public static String getPlaylistIndex() {
            return "javascript:"
                    + YoutubeJsInterface.JSI_ON_GET_PLAYLIST_INDEX + "(player.getPlaylistIndex());";
        }

        public static String getPlaylist() {
            return "javascript:"
                    + YoutubeJsInterface.JSI_ON_GET_PLAYLIST + "(player.getPlaylist());";
        }

        public static String getVideoId() {
            return "javascript:"
                    + YoutubeJsInterface.JSI_ON_GET_VID + "(player.getVideoData()['video_id']);";
        }
    }

    public static String getVideoHTML(String videoId) {
        return VIDEO_PLAYLIST_HTML_PATTERN
                .replace(PLACEHOLDER_IFRAME_ELEMENT, getIFrameElement(null, videoId));
    }

    public static String getPlayListHTML(String playlistId, @Nullable String videoId) {
        return VIDEO_PLAYLIST_HTML_PATTERN
                .replace(PLACEHOLDER_IFRAME_ELEMENT, getIFrameElement(playlistId, videoId));
    }

    private static final String PLACEHOLDER_IFRAME_ELEMENT = "%s";
    private static final String VIDEO_PLAYLIST_HTML_PATTERN =
            "<!DOCTYPE HTML>\n" +
            "<html>\n" +
            "  <head>\n" +
            "    <script src=\"https://www.youtube.com/iframe_api\"></script>\n" +
            "    <style type=\"text/css\">\n" +
            "        html, body {\n" +
            "            margin: 0px;\n" +
            "            padding: 0px;\n" +
            "            border: 0px;\n" +
            "            width: 100%;\n" +
            "            height: 100%;\n" +
            "        }\n" +
            "    </style>\n" +
            "  </head>\n" +
            "\n" +
            "  <body>\n" +
            PLACEHOLDER_IFRAME_ELEMENT +
            "    <script type=\"text/javascript\">\n" +
            "      var tag = document.createElement('script');\n" +
            "      tag.src = \"https://www.youtube.com/iframe_api\";\n" +
            "      var firstScriptTag = document.getElementsByTagName('script')[0];\n" +
            "      firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);\n" +
            "      var player;\n" +
            "      function onYouTubeIframeAPIReady() {\n" +
            "          player = new YT.Player('player', {\n" +
            "              events: {\n" +
            "                  'onReady': onPlayerReady,\n" +
            "                  'onStateChange': onPlayerStateChange\n" +
            "              }\n" +
            "          });\n" +
            "      }\n" +
            "      function onPlayerReady(event) {\n" +
            "          " + YoutubeJsInterface.JSI_ON_PLAYER_READY + "();\n" +
            "      }\n" +
            "      function onPlayerStateChange(event) {\n" +
            "          " + YoutubeJsInterface.JSI_ON_PLAYER_STATE_CHANGE + "(player.getPlayerState());\n" +
            "      }\n" +
            "    </script>\n" +
            "\n" +
            "  </body>\n" +
            "</html>";

    private static String getIFrameElement(@Nullable String playlistId, @Nullable String videoId) {
        return "    <iframe style=\"display: block;\"\n" +
               "            id=\"player\"\n" +
               "            frameborder=\"0\"\n" +
               "            width=\"100%\"\n" +
               "            height=\"100%\"\n" +
               "            src=\"https://www.youtube.com/embed/" + (videoId == null ? "" : videoId) +
               "?" + (TextUtils.isEmpty(playlistId) ? "" : ("list=" + playlistId + "&")) +
               "enablejsapi=1" +
               "&autoplay=1" +
               "&fs=" + EmbeddedPlayerConfigs.SHOW_FULLSCREEN_BUTTON +
               "&modestbranding=" + EmbeddedPlayerConfigs.HIDE_YOUTUBE_LOGO +
               "&iv_load_policy="+ EmbeddedPlayerConfigs.SHOW_VIDEO_ANNOTATIONS +
               "&rel=" + EmbeddedPlayerConfigs.SHOW_RELATED_VIDEOS + "\">\n" +
               "    </iframe>\n";
    }

    private static final class EmbeddedPlayerConfigs {
        static final int SHOW_FULLSCREEN_BUTTON = 0;
        static final int HIDE_YOUTUBE_LOGO = 0;
        static final int SHOW_VIDEO_ANNOTATIONS = 3; // Use 1 to show
        static final int SHOW_RELATED_VIDEOS = 0;
    }
}
