package com.liuzhenlin.videos.web.youtube;

import android.content.Context;
import android.webkit.JavascriptInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.videos.web.VideosJsInterface;
import com.liuzhenlin.videos.web.bean.Playlist;
import com.liuzhenlin.videos.web.bean.Video;

public class YoutubeJsInterface extends VideosJsInterface {

    public static final String NAME = "YouTube";

    public static final String JSI_ON_EVENT = "window.YouTube.onEvent";
    public static final String JSI_ON_PLAYER_READY = "window.YouTube.onPlayerReady";
    public static final String JSI_ON_PLAYER_STATE_CHANGE = "window.YouTube.onPlayerStateChange";

    public static final int JSE_VIDEO_SELECTOR_FOUND = JSE_LAST + 1;

    public static final int JSE_VIDEO_PLAYING = JSE_LAST + 2;
    public static final int JSE_VIDEO_PAUSED = JSE_LAST + 3;
    public static final int JSE_VIDEO_ENDED = JSE_LAST + 4;
    public static final int JSE_VIDEO_BUFFERING = JSE_LAST + 5;
    public static final int JSE_VIDEO_CUED = JSE_LAST + 6;
    public static final int JSE_VIDEO_UNSTARTED = JSE_LAST + 7;

    public static final int JSE_VIDEO_INFO_RETRIEVED = JSE_LAST + 8;
    public static final int JSE_PLAYLIST_INFO_RETRIEVED = JSE_LAST + 9;

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
            case JSE_VIDEO_INFO_RETRIEVED:
                onRetrieveVideoInfo(data);
                break;
            case JSE_PLAYLIST_INFO_RETRIEVED:
                onRetrievePlaylistInfo(data);
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
    public void onRetrieveVideoInfo(String json) {
        Video video = new Gson().fromJson(json, Video.class);
        Executors.MAIN_EXECUTOR.execute(
                () -> YoutubePlaybackService.peekIfNonnullThenDo(
                        service -> service.onGetVideoInfo(video)));
    }

    @JavascriptInterface
    public void onRetrievePlaylistInfo(String json) {
        Playlist playlist = new Gson().fromJson(json, Playlist.class);
        Executors.MAIN_EXECUTOR.execute(
                () -> YoutubePlaybackService.peekIfNonnullThenDo(
                        service -> service.onGetPlaylistInfo(playlist)));
    }
}