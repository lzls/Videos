package com.liuzhenlin.videos.web.youtube;

import android.content.Context;
import android.webkit.JavascriptInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.videos.web.VideosJsInterface;

public class YoutubeJsInterface extends VideosJsInterface {

    public static final String NAME = "YouTube";

    public static final String JSI_ON_EVENT = "window.YouTube.onEvent";
    public static final String JSI_ON_PLAYER_READY = "window.YouTube.onPlayerReady";
    public static final String JSI_ON_PLAYER_STATE_CHANGE = "window.YouTube.onPlayerStateChange";
    public static final String JSI_ON_GET_VID = "window.YouTube.onGetVideoId";
    public static final String JSI_ON_GET_PLAYLIST = "window.YouTube.onGetPlaylist";
    public static final String JSI_ON_GET_PLAYLIST_INDEX = "window.YouTube.onGetPlaylistIndex";

    public static final int JSE_VIDEO_SELECTOR_FOUND = JSE_LAST + 1;
    public static final int JSE_VIDEO_PLAYING = JSE_LAST + 2;
    public static final int JSE_VIDEO_PAUSED = JSE_LAST + 3;
    public static final int JSE_VIDEO_ENDED = JSE_LAST + 4;
    public static final int JSE_VIDEO_BUFFERING = JSE_LAST + 5;
    public static final int JSE_VIDEO_CUED = JSE_LAST + 6;
    public static final int JSE_VIDEO_UNSTARTED = JSE_LAST + 7;

    public YoutubeJsInterface(@NonNull Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected String logTag() {
        return Youtube.JS_LOG_TAG;
    }

    @Override
    protected void handleEvent(int event, @Nullable String data) {
        super.handleEvent(event, data);
        switch (event) {
            case JSE_VIDEO_SELECTOR_FOUND:
                onPlayerReady();
                break;
            case JSE_VIDEO_PLAYING:
                onPlayerStateChange(Youtube.PlayingStatus.PLAYING);
                break;
            case JSE_VIDEO_PAUSED:
                onPlayerStateChange(Youtube.PlayingStatus.PAUSED);
                break;
            case JSE_VIDEO_ENDED:
                onPlayerStateChange(Youtube.PlayingStatus.ENDED);
                break;
            case JSE_VIDEO_BUFFERING:
                onPlayerStateChange(Youtube.PlayingStatus.BUFFERRING);
                break;
            case JSE_VIDEO_CUED:
                onPlayerStateChange(Youtube.PlayingStatus.VIDEO_CUED);
                break;
            case JSE_VIDEO_UNSTARTED:
                onPlayerStateChange(Youtube.PlayingStatus.UNSTARTED);
                break;
        }
    }

    @JavascriptInterface
    public void onPlayerReady() {
        Executors.MAIN_EXECUTOR.execute(
                () -> YoutubePlaybackService.peekIfNonnullThenDo(YoutubePlaybackService::onPlayerReady));
    }

    @JavascriptInterface
    public void onPlayerStateChange(int status) {
        Executors.MAIN_EXECUTOR.execute(() -> {
            if (YoutubePlaybackActivity.get() != null) {
                YoutubePlaybackActivity.get().onPlayingStatusChange(status);
            }
            YoutubePlaybackService.peekIfNonnullThenDo(service -> service.onPlayingStatusChange(status));
        });
    }

    @JavascriptInterface
    public void onGetVideoId(String vid) {
        Executors.MAIN_EXECUTOR.execute(() ->
                YoutubePlaybackService.peekIfNonnullThenDo(service -> service.onGetVideoId(vid)));
    }

    @JavascriptInterface
    public void onGetPlaylist(String[] vids) {
        Executors.MAIN_EXECUTOR.execute(
                () -> YoutubePlaybackService.peekIfNonnullThenDo(service -> service.onGetPlaylist(vids)));
    }

    @JavascriptInterface
    public void onGetPlaylistIndex(int index) {
        Executors.MAIN_EXECUTOR.execute(() ->
                YoutubePlaybackService.peekIfNonnullThenDo(service -> service.onGetPlaylistIndex(index)));
    }
}