/*
 * Created on 2022-2-18 4:40:56 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.bumptech.glide.util.Preconditions;
import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.common.utils.Regex;
import com.liuzhenlin.common.utils.prefs.PrefsHelper;
import com.liuzhenlin.videos.web.player.Constants;
import com.liuzhenlin.videos.web.player.Constants.Keys;
import com.liuzhenlin.videos.web.player.Constants.VideoQuality;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.liuzhenlin.common.utils.Utils.roundDouble;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSE_PLAYLIST_INFO_RETRIEVED;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSE_VIDEO_INFO_RETRIEVED;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSI_ON_EVENT;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSI_ON_PLAYER_READY;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSI_ON_PLAYER_STATE_CHANGE;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSE_ERR;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSE_VIDEO_BUFFERING;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSE_VIDEO_ENDED;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSE_VIDEO_PAUSED;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSE_VIDEO_PLAYING;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSE_VIDEO_SELECTOR_FOUND;
import static com.liuzhenlin.videos.web.youtube.YoutubeJsInterface.JSE_VIDEO_UNSTARTED;

@NonNullApi
public final class Youtube {

    private Youtube() {}

    /*package*/ static final String JS_LOG_TAG = "YouTubeJavaScripts";

    /*package*/ static final String REGEX_PROTOCOL = "http(s)?://";
    /*package*/ static final String REGEX_WATCH_URL_HOST = "(m|www)\\.youtube\\.com";
    /*package*/ static final String REGEX_SHARE_URL_HOST = "youtu\\.be";

    public static final Regex REGEX_WATCH_URL = new Regex(
            "^(" + REGEX_PROTOCOL + REGEX_WATCH_URL_HOST + "/watch\\?).+");
    public static final Regex REGEX_SHORTS_URL = new Regex(
            "^(" + REGEX_PROTOCOL + REGEX_WATCH_URL_HOST + "/shorts/).+");
    public static final Regex REGEX_SHARE_URL = new Regex(
            "^" + REGEX_PROTOCOL + REGEX_SHARE_URL_HOST + "/((\\?list=)?[A-Za-z0-9_-]+)+.*");

    // For mobile terminal
    public static final class URLs {
        private URLs() {}

        public static final String HOME = "https://m.youtube.com";

        public static final String PLAYER_API = HOME + "/player_api";

        public static final String WATCH = HOME + "/watch";

        public static final String SHORTS = HOME + "/shorts";
    }

    @IntDef({
            PlayingStatus.UNSTARTED, PlayingStatus.ENDED, PlayingStatus.PLAYING,
            PlayingStatus.PAUSED, PlayingStatus.BUFFERRING, PlayingStatus.VIDEO_CUED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PlayingStatus {
        int UNSTARTED = -1; // When the player first loads a video
        int ENDED = 0;
        int PLAYING = 1;
        int PAUSED = 2;
        int BUFFERRING = 3;
        int VIDEO_CUED = 5; // When a video is cued and ready to play
    }

    public static abstract class Prefs {

        public static final String KEY_PLAYBACK_PAGE_STYLE = "youtube_playback_page_style";
        public static final String PLAYBACK_PAGE_STYLE_ORIGINAL = "original";
        public static final String PLAYBACK_PAGE_STYLE_BRIEF = "brief";

        public static final String KEY_PIP = "youtube_pip";

        public static final String KEY_RETAIN_HISTORY_VIDEO_PAGES =
                "youtube_retain_history_video_pages";

        public static final String KEY_VIDEO_QUALITY = "youtube_video_quality";

        @SuppressWarnings("NotNullFieldNotInitialized")
        private static Prefs sImpl;

        public static Prefs get(Context context) {
            //noinspection ConstantConditions
            if (sImpl == null) {
                sImpl = new Prefs() {
                    final PrefsHelper mPrefsHelper = PrefsHelper.create(
                            context,
                            /* PreferenceManager.getDefaultSharedPreferencesName(context) */
                            context.getPackageName() + "_preferences");

                    @Override
                    public String getPlaybackPageStyle() {
                        return mPrefsHelper.getString(KEY_PLAYBACK_PAGE_STYLE,
                                PLAYBACK_PAGE_STYLE_ORIGINAL);
                    }

                    @Override
                    public boolean enterPipWhenVideoIsFullscreenAndPlaybackSwitchesToBackground() {
                        return mPrefsHelper.getBoolean(KEY_PIP, false);
                    }

                    @Override
                    public boolean retainHistoryVideoPages() {
                        return mPrefsHelper.getBoolean(KEY_RETAIN_HISTORY_VIDEO_PAGES, true);
                    }

                    @Override
                    public String getVideoQuality() {
                        return mPrefsHelper.getString(KEY_VIDEO_QUALITY, VideoQuality.AUTO);
                    }
                };
            }
            return sImpl;
        }

        public static void set(Prefs prefs) {
            sImpl = Preconditions.checkNotNull(prefs);
        }

        public abstract String getPlaybackPageStyle();

        public abstract boolean enterPipWhenVideoIsFullscreenAndPlaybackSwitchesToBackground();

        public abstract boolean retainHistoryVideoPages();

        public abstract String getVideoQuality();
    }

    public static final class IFrameJsInterface {
        private IFrameJsInterface() {}

        public static String loadVideo(String vId, long startMs) {
            String js = "javascript:player.loadVideoById({videoId:\"" + vId + "\"";
            if (startMs != Constants.TIME_UNSET) {
                js += ", startSeconds:" + (startMs / 1000d);
            }
            js += "});";
            return js;
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

        public static String seekTo(long positionMs) {
            return "javascript:"
                    + "player.seekTo(" + roundDouble(positionMs / 1000d) + ", true);";
        }

        public static String fastRewind() {
            return "javascript:player.seekTo(player.getCurrentTime() -15, true);";
        }

        public static String fastForward() {
            return "javascript:player.seekTo(player.getCurrentTime() + 15, true);";
        }

        public static String setPlaybackQuality(String quality) {
            // The getPlaybackQuality function is no longer supported.
            // In particular, calls to setPlaybackQuality will be no-op functions,
            // meaning they will not actually have any impact on the viewer's playback experience.
            return "javascript:player.setPlaybackQuality(\"" + quality + "\");";
        }

        public static String setMuted(boolean muted) {
            return "javascript:player." + (muted ? "mute" : "unMute") + "();";
        }

        public static String loadPlaylist(String pId, int index, long startMs) {
            String js = "javascript:player.loadPlaylist({list:\"" + pId + "\", index:" + index;
            if (startMs != Constants.TIME_UNSET) {
                js += ", startSeconds:" + (startMs / 1000d);
            }
            js += "});";
            return js;
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

        public static String getPlaylistInfo() {
            return "javascript:\n" +
                    "var playlist = player.getPlaylist();\n" +
                    "var playlistIndex = player.getPlaylistIndex();\n" +
                    "var infoObj = new Object();\n" +
                    "infoObj['" + Keys.PLAYLIST + "'] = playlist;\n" +
                    "infoObj['" + Keys.PLAYLIST_INDEX + "'] = playlistIndex;\n" +
                    JSI_ON_EVENT + "(" + JSE_PLAYLIST_INFO_RETRIEVED + ", JSON.stringify(infoObj));";
        }

        public static String getVideoInfo(boolean refreshNotificationOnInfoRetrieved) {
            return "javascript:\n" +
                    "var videoId = player.getVideoData()['video_id'];\n" +
                    "var fduration = player.getDuration() * 1000;\n" +
                    "var duration = Math.round(fduration);\n" +
                    "var bufferedPosition = Math.round(fduration * player.getVideoLoadedFraction());\n" +
                    "var currentPosition = Math.round(player.getCurrentTime() * 1000);\n" +
                    "var infoObj = new Object();\n" +
                    "infoObj['" + Keys.ID + "'] = videoId;\n" +
                    "infoObj['" + Keys.DURATION + "'] = duration;\n" +
                    "infoObj['" + Keys.BUFFERED_POSITION + "'] = bufferedPosition;\n" +
                    "infoObj['" + Keys.CURRENT_POSITION + "'] = currentPosition;\n" +
                    "infoObj['" + Keys.REFRESH_NOTIFICATION + "'] = "
                    + refreshNotificationOnInfoRetrieved + ";\n" +
                    JSI_ON_EVENT + "(" + JSE_VIDEO_INFO_RETRIEVED + ", JSON.stringify(infoObj));";
        }
    }

    public static final class JsInterface {
        private JsInterface() {}

        public static String attachListeners(Context context) {
            return "javascript:\n" +
                    "function attachVideoListeners(v) {\n" +
                    "  if (v.getAttribute('listenersAttached') === 'true') return;\n" +
                    "  v.setAttribute('listenersAttached', 'true');\n" +
                    "  " + JSI_ON_EVENT + "(" + JSE_VIDEO_SELECTOR_FOUND + ", null);\n" +
                    "  if (v.currentTime > 0 && !v.paused && !v.ended) " + JSI_ON_EVENT
                    + "(" + JSE_VIDEO_PLAYING + ", v.currentSrc);\n" +
                    "  v.addEventListener('playing', function(e) {" + JSI_ON_EVENT
                    + "(" + JSE_VIDEO_PLAYING + ", v.currentSrc);});\n" +
                    "  v.addEventListener('pause', function(e) {" + JSI_ON_EVENT
                    + "(" + JSE_VIDEO_PAUSED + ", v.currentSrc);});\n" +
                    "  v.addEventListener('ended', function(e) {" + JSI_ON_EVENT
                    + "(" + JSE_VIDEO_ENDED + ", v.currentSrc);});\n" +
                    "  v.addEventListener('waiting', function(e) {" + JSI_ON_EVENT
                    + "(" + JSE_VIDEO_BUFFERING + ", null);});\n" +
                    "  v.addEventListener('loadstart', function(e) {" + JSI_ON_EVENT
                    + "(" + JSE_VIDEO_UNSTARTED + ", null);});\n" +
                    "  v.addEventListener('loadedmetadata', function(e) {\n" +
                    "    if (v.getAttribute('qualitySet') === 'true') return;\n" +
                    "    v.setAttribute('qualitySet', 'true');\n" +
                    "    " + setPlaybackQuality(Prefs.get(context).getVideoQuality())
                                    .replace("javascript:", "") + "\n" +
                    "  });\n" +
                    "}\n" +
                    "function findVideo() {\n" +
                    "  var video = document.querySelectorAll('video');\n" +
                    "  video.forEach(attachVideoListeners);\n" +
                    "  setTimeout(findVideo, 1000);\n" +
                    "}\n" +
                    "findVideo();";
        }

        public static String skipAd() {
            return "javascript:\n" +
                    "function skipAdPoster(attempt) {\n" +
                    "  var btn = document.querySelector('.ytp-ad-skip-button');\n" +
                    "  if (btn != null) {\n" +
                    "    console.debug('Clicking skip-ad button...');\n" +
                    "    btn.click();\n" +
                    "    return true;\n" +
                    "  }\n" +
                    "  if (attempt < 10) {\n" +
                    "    console.debug('Retry skipping AD poster...');\n" +
                    "    setTimeout(skipAdPoster, 100, attempt + 1);\n" +
                    "    return false;\n" +
                    "  }\n" +
                    "  if (document.querySelector('.ad-showing') != null) {\n" +
                    "    " + JSI_ON_EVENT + "(" + JSE_ERR + ", 'Failed to skip AD poster');\n" +
                    "    return false;\n" +
                    "  }\n" +
                    "  console.debug('No AD poster to skip');\n" +
                    "  return true;\n" +
                    "}\n" +
                    "if (document.querySelector('.ad-showing') != null) {\n" +
                    "  let video = document.querySelector('video');\n" +
                    "  if (video != null) {\n" +
                    "    video.currentTime = video.duration;\n" +
                    "    console.debug('Start skipping AD poster...');\n" +
                    "    setTimeout(skipAdPoster, 100, 0);\n" +
                    "  }\n" +
                    "}";
        }

        public static String setMuted(boolean muted) {
            return "javascript:var v = document.querySelector('video');\n" +
                    "if (v != null) v.muted=" + muted + ";";
        }

        public static String loadVideo(String vId, long startMs) {
            return URLs.WATCH + "?v=" + vId
                    + (startMs == Constants.TIME_UNSET ? "" : "&t=" + (startMs / 1000d) + "s");
        }

        public static String playVideo() {
            return "javascript:var v = document.querySelector('video'); if (v != null) v.play();";
        }

        public static String pauseVideo() {
            return "javascript:var v = document.querySelector('video'); if (v != null) v.pause();";
        }

        public static String stopVideo() {
            return "javascript:var v = document.querySelector('video');\n" +
                    "if (v != null) { v.currentTime = 0; v.pause(); }";
        }

        public static String nextVideo() {
            return prevNextVideo(4, 1);
        }

        public static String prevVideo() {
            return prevNextVideo(0, 0);
        }

        private static String prevNextVideo(int idx, int plIdx) {
            return "javascript:\n" +
                    "var e = document.getElementsByClassName('player-controls-middle center');\n" +
                    "if (e.length > 0) e = e[0].querySelectorAll('button');\n" +
                    "if (e.length >= 5) e[" + idx + "].click();\n" +
                    "else {\n" +
                    "  e = document.getElementsByClassName('playlist-controls-primary');\n" +
                    "  if (e.length > 0 && e[0].children.length >= 2)\n" +
                    "    e[0].children[" + plIdx + "].children[0].click();\n" +
                    "}";
        }

        public static String seekToDefault() {
            return seekTo(0);
        }

        public static String seekTo(long positionMs) {
            double pos = positionMs / 1000d;
            return "javascript:var v = document.querySelector('video');\n" +
                    "if (v != null) v.currentTime = " + pos + ";";
        }

        public static String fastRewind() {
            return "javascript:var v = document.querySelector('video');\n" +
                    "if (v != null) v.currentTime -= 15;";
        }

        public static String fastForward() {
            return "javascript:var v = document.querySelector('video');\n" +
                    "if (v != null) v.currentTime += 15;";
        }

        public static String setPlaybackQuality(String quality) {
            return "javascript:\n" +
                    "function retrySetVideoQuality(quality, attempt, openMenu) {\n" +
                    "  if (attempt < 10)\n" +
                    "   setTimeout(setVideoQuality, 100, quality, attempt + 1, openMenu);\n" +
                    "  else " + JSI_ON_EVENT + "(" + JSE_ERR
                    + ", 'Failed to set playback quality to " + quality + "');\n" +
                    "  return false;\n" +
                    "}\n" +
                    "function setVideoQuality(quality, attempt, openMenu) {\n" +
                    "  if (openMenu) {\n" +
                    "    var b = document.querySelector('.player-settings-icon');\n" +
                    "    if (b == null) return retrySetVideoQuality(quality, attempt, true);\n" +
                    "    b.click();\n" +
                    "  }\n" +
                    "  var settings = document.querySelector('.player-quality-settings');\n" +
                    "  if (settings == null) return retrySetVideoQuality(quality, attempt, false);\n" +
                    "  var select = settings.querySelector('.select');\n" +
                    "  if (select == null) return retrySetVideoQuality(quality, attempt, false);\n" +
                    "  var options = select.querySelectorAll('.option');\n" +
                    "  var idx = options.length - 1;\n" +
                    "  quality = quality.trim().toLowerCase();\n" +
                    "  if (!quality.match(/^" + VideoQuality.AUTO + "$/i)) {\n" +
                    "    for (let i = 0; i < options.length - 1; i++) {\n" +
                    "      var option = options[i].innerText.toLowerCase();\n" +
                    "      if (parseInt(option.substring(0, option.indexOf('p')))\n" +
                    "          <= parseInt(quality.substring(0, quality.indexOf('p')))) {\n" +
                    "        idx = i;\n" +
                    "        break;\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "  if (idx != select.selectedIndex) {\n" +
                    "    var evt = document.createEvent(\"HTMLEvents\");\n" +
                    "    evt.initEvent(\"change\", true, true);\n" +
                    "    select.selectedIndex = idx;\n" +
                    "    options[idx].selected = true;\n" +
                    "    select.dispatchEvent(evt);\n" +
                    "  }\n" +
                    "  setTimeout(()=> {settings.parentNode.parentNode.querySelector("
                    + "'.c3-material-button-button').click();}, 100);\n" +
                    "  return true;\n" +
                    "}\n" +
                    "setVideoQuality('" + quality + "', 0, true);";
        }

        public static String loadPlaylist(String pId, @Nullable String vId, int index, long startMs) {
            return URLs.WATCH + "?list=" + pId
                    + (TextUtils.isEmpty(vId) ? "" : "&v=" + vId)
                    + (index == Constants.UNKNOWN ? "" : "&index=" + index)
                    + (startMs == Constants.TIME_UNSET ? "" : "&t=" + (startMs / 1000d) + "s");
        }

        public static String setLoopPlaylist(boolean loop) {
            return "javascript:";
        }

        public static String replayPlaylist() {
            return "javascript:\n" +
                    "var e = document.getElementsByClassName('player-controls-middle center');\n" +
                    "if (e.length > 0)\n" +
                    "  e = e[0].getElementsByClassName('icon-button endscreen-replay-button');\n" +
                    "if (e.length > 0) e[0].click();\n" +
                    "else {\n" +
                    "  " + playVideoAt(0).replace("javascript:", "") + "\n" +
                    "}";
        }

        public static String playVideoAt(int index) {
            return "javascript:\n" +
                    "var e = document.getElementsByClassName('playlist-content section');\n" +
                    "if (e.length > 0) {\n" +
                    "  e = e[0].getElementsByClassName('compact-media-item');\n" +
                    "  if (e.length > " + index + ") {\n" +
                    "    e = e[" + index + "].querySelector('a');\n" +
                    "    if (e != null) e.click();\n" +
                    "  } else {\n" +
                    "    " + JSI_ON_EVENT + "(" + JSE_ERR
                    + ", 'Expected maximum index for video to be played ' + e.length + "
                    + "', but got ' + " + index + ");\n" +
                    "  }\n" +
                    "} else if (" + index + " == 0) {\n" +
                    "  " + playVideo().replace("javascript:", "") + "\n" +
                    "} else {\n" +
                    "  " + JSI_ON_EVENT + "(" + JSE_ERR
                    + ", 'Expected maximum index for video to be played 0, but got ' + " + index + ");\n" +
                    "}";
        }

        private static String jsFunGetPlaylistIndex() {
            return "function getPlaylistIndex() {\n" +
                    "  var e = document.getElementsByClassName('playlist-content section');\n" +
                    "  if (e.length > 0) {\n" +
                    "    e = e[0].querySelectorAll('ytm-playlist-panel-video-renderer');\n" +
                    "    for (let i = e.length - 1; i >= 0; i--) {\n" +
                    "      if (e[i].getAttribute('selected') == 'true') {\n" +
                    "        return i;\n" +
                    "      }\n" +
                    "    }\n" +
                    "  } else {\n" +
                    "    return 0;\n" +
                    "  }\n" +
                    "  return -1;\n" +
                    "}";
        }

        private static String jsFunGetPlaylist() {
            return "function getPlaylist() {\n" +
                    "  var e = document.getElementsByClassName('playlist-content section');\n" +
                    "  if (e.length > 0) {\n" +
                    "    e = e[0].getElementsByClassName('compact-media-item');\n" +
                    "    if (e.length > 0) {\n" +
                    "      var videos = new Array();\n" +
                    "      for (let i = e.length - 1; i >= 0; i--) {\n" +
                    "        let href = e[i].querySelector('a').href;\n" +
                    "        videos[i] = href.substring(href.indexOf('v=') + 2).split('&', 2)[0];\n" +
                    "      }\n" +
                    "      return videos;\n" +
                    "    }\n" +
                    "  } else {\n" +
                    "    " + jsFunGetVideoId() + "\n" +
                    "    return [getVideoId()];\n" +
                    "  }\n" +
                    "  return null;\n" +
                    "}";
        }

        private static String jsFunGetPlaylistId() {
            return "function getPlaylistId() {\n" +
                    "  var e = document.getElementsByClassName('playlist-content section');\n" +
                    "  if (e.length > 0) {\n" +
                    "    e = e[0].querySelector('div.compact-media-item');\n" +
                    "    if (e != null) {\n" +
                    "      let href = e.querySelector('a').href;\n" +
                    "      let pid = href.substring(href.indexOf('list=') + 5).split('&', 2)[0];\n" +
                    "      return pid;\n" +
                    "    }\n" +
                    "  }\n" +
                    "  return null;\n" +
                    "}";
        }

        private static String jsFunGetVideoId() {
            return "function getVideoId() {\n" +
                    "  var e = document.querySelector('ytm-player-microformat-renderer');\n" +
                    "  if (e != null) {\n" +
                    "    let json = JSON.parse(e.firstChild.innerText);\n" +
                    "    let embedUrl = json.embedUrl;\n" +
                    "    let embedUrlInfix = 'youtube.com/embed/';\n" +
                    "    let vid = embedUrl.substring(embedUrl.indexOf(embedUrlInfix)"
                    + " + embedUrlInfix.length).split('?', 2)[0];\n" +
                    "    return vid;\n" +
                    "  }\n" +
                    "  return null;\n" +
                    "}";
        }

        private static String jsFunGetVideoDuration() {
            return "function getVideoDuration() {\n" +
                    "  var v = document.querySelector('video');\n" +
                    "  return v != null ? Math.round(v.duration * 1000) : 0;\n" +
                    "}";
        }

        private static String jsFunGetVideoBufferedPosition() {
            return "function getVideoBufferedPosition() {\n" +
                    "  var v = document.querySelector('video');\n" +
                    "  if (v == null) return 0;\n" +
                    "  var bufferedPosition = 0;\n" +
                    "  for (let i = v.buffered.length - 1; i >= 0; i--) {;\n" +
                    "    let end = v.buffered.end(i);\n" +
                    "    if (end > bufferedPosition) bufferedPosition = end;\n" +
                    "  }\n" +
                    "  return Math.round(bufferedPosition * 1000);\n" +
                    "}";
        }

        private static String jsFunGetVideoCurrentPosition() {
            return "function getVideoCurrentPosition() {\n" +
                    "  var v = document.querySelector('video');\n" +
                    "  return v != null ? Math.round(v.currentTime * 1000) : 0;\n" +
                    "}";
        }

        public static String getPlaylistInfo() {
            return "javascript:\n" +
                    jsFunGetPlaylistId() + "\n" +
                    jsFunGetPlaylist() + "\n" +
                    jsFunGetPlaylistIndex() + "\n" +
                    "var playlistId = getPlaylistId();\n" +
                    "var playlist = getPlaylist();\n" +
                    "var playlistIndex = getPlaylistIndex();\n" +
                    "var infoObj = new Object();\n" +
                    "infoObj['" + Keys.ID + "'] = playlistId;\n" +
                    "infoObj['" + Keys.PLAYLIST + "'] = playlist;\n" +
                    "infoObj['" + Keys.PLAYLIST_INDEX + "'] = playlistIndex;\n" +
                    JSI_ON_EVENT + "(" + JSE_PLAYLIST_INFO_RETRIEVED + ", JSON.stringify(infoObj));";
        }

        public static String getVideoInfo(boolean refreshNotificationOnInfoRetrieved) {
            return "javascript:\n" +
                    jsFunGetVideoId() + "\n" +
                    jsFunGetVideoDuration() + "\n" +
                    jsFunGetVideoBufferedPosition() + "\n" +
                    jsFunGetVideoCurrentPosition() + "\n" +
                    "var videoId = getVideoId();\n" +
                    "var duration = getVideoDuration();\n" +
                    "var bufferedPosition = getVideoBufferedPosition();\n" +
                    "var currentPosition = getVideoCurrentPosition();\n" +
                    "var infoObj = new Object();\n" +
                    "infoObj['" + Keys.ID + "'] = videoId;\n" +
                    "infoObj['" + Keys.DURATION + "'] = duration;\n" +
                    "infoObj['" + Keys.BUFFERED_POSITION + "'] = bufferedPosition;\n" +
                    "infoObj['" + Keys.CURRENT_POSITION + "'] = currentPosition;\n" +
                    "infoObj['" + Keys.REFRESH_NOTIFICATION + "'] = "
                    + refreshNotificationOnInfoRetrieved + ";\n" +
                    JSI_ON_EVENT + "(" + JSE_VIDEO_INFO_RETRIEVED + ", JSON.stringify(infoObj));";
        }

        public static String requestFullscreen() {
            return "javascript:\n" +
                    "function requestFullscreen(attempt) {\n" +
                    "  var e = document.getElementsByClassName('player-controls-bottom cbox');\n" +
                    "  if (e.length <= 0) return retryRequestFullscreen(attempt + 1);\n" +
                    "  e = e[0].getElementsByClassName('icon-button fullscreen-icon');\n" +
                    "  if (e.length <= 0) return retryRequestFullscreen(attempt + 1);\n" +
                    "  e[0].click();\n" +
                    "  return true;\n" +
                    "}\n" +
                    "function retryRequestFullscreen(attempt) {\n" +
                    "  if (attempt < 10) setTimeout(requestFullscreen, 100, attempt);\n" +
                    "  else " + JSI_ON_EVENT + "(" + JSE_ERR
                    + ", 'Failed to request video to be fullscreen');\n" +
                    "  return false;\n" +
                    "}\n" +
                    "requestFullscreen(0);";
        }
    }

    public static final class Util {
        private Util() {}

        @Nullable
        public static String getPlaylistIdFromWatchOrShareUrl(String url) {
            url = normalizedServerUrl(url);
            int startOfListId = url.indexOf("list=");
            if (startOfListId > 0) {
                return url.substring(startOfListId + 5).split("&", 2)[0];
            }
            return null;
        }

        @Nullable
        public static String getVideoIdFromWatchUrl(String watchUrl) {
            watchUrl = normalizedServerUrl(watchUrl);
            int startOfVideoId = watchUrl.indexOf("v=");
            if (startOfVideoId > 0) {
                return watchUrl.substring(startOfVideoId + 2).split("&", 2)[0];
            }
            return null;
        }

        @Nullable
        public static String getVideoIdFromShareUrl(String shareUrl) {
            shareUrl = normalizedServerUrl(shareUrl);
            int startOfVideoId = shareUrl.indexOf("youtu.be/");
            if (startOfVideoId > 0) {
                return shareUrl.substring(startOfVideoId + 9).split("\\?", 2)[0];
            }
            return null;
        }

        public static int getVideoIndexFromWatchOrShareUrl(String url) {
            url = normalizedServerUrl(url);
            int startOfVideoIndex = url.indexOf("index=");
            if (startOfVideoIndex > 0) {
                try {
                    return Integer.parseInt(url.substring(startOfVideoIndex + 6).split("&", 2)[0]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
            return Constants.UNKNOWN;
        }

        public static long getVideoStartMsFromWatchOrShareUrl(String url) {
            url = normalizedServerUrl(url);
            int startOfVideoStartTime = url.indexOf("t=");
            if (startOfVideoStartTime > 0) {
                try {
                    String s = url.substring(startOfVideoStartTime + 2).split("&", 2)[0]
                            .replace("s", "");
                    return Long.parseLong(s) * 1000L;
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
            return Constants.TIME_UNSET;
        }

        private static String normalizedServerUrl(String url) {
            return url.split("#", 2)[0];
        }
    }

    public static String getVideoHTML(String videoId, long startMs) {
        return VIDEO_PLAYLIST_HTML_PATTERN
                .replace(PLACEHOLDER_IFRAME_ELEMENT, getIFrameElement(null, videoId, startMs));
    }

    public static String getPlayListHTML(String playlistId, @Nullable String videoId, long startMs) {
        return VIDEO_PLAYLIST_HTML_PATTERN
                .replace(PLACEHOLDER_IFRAME_ELEMENT, getIFrameElement(playlistId, videoId, startMs));
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
            "          " + JSI_ON_PLAYER_READY + "();\n" +
            "      }\n" +
            "      function onPlayerStateChange(event) {\n" +
            "          " + JSI_ON_PLAYER_STATE_CHANGE + "(player.getPlayerState());\n" +
            "      }\n" +
            "    </script>\n" +
            "\n" +
            "  </body>\n" +
            "</html>";

    private static String getIFrameElement(
            @Nullable String playlistId, @Nullable String videoId, long startMs) {
        return "    <iframe style=\"display: block;\"\n" +
               "            id=\"player\"\n" +
               "            frameborder=\"0\"\n" +
               "            width=\"100%\"\n" +
               "            height=\"100%\"\n" +
               "            src=\"https://www.youtube.com/embed/" + (videoId == null ? "" : videoId) +
               "?" + (TextUtils.isEmpty(playlistId) ? "" : ("list=" + playlistId + "&")) +
               (startMs == Constants.TIME_UNSET ? "" : ("start=" + roundDouble(startMs / 1000d) + "&")) +
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
