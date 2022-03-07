package com.liuzhenlin.videos.web.youtube;

import android.content.Context;
import android.webkit.JavascriptInterface;

import com.liuzhenlin.common.utils.Executors;

public class YoutubeJsInterface {

    public static final String NAME = "YouTube";
    public static final String JSI_ON_PLAYER_READY = "window.YouTube.onPlayerReady";
    public static final String JSI_ON_PLAYER_STATE_CHANGE = "window.YouTube.onPlayerStateChange";
    public static final String JSI_ON_GET_VID = "window.YouTube.onGetVideoId";
    public static final String JSI_ON_GET_PLAYLIST = "window.YouTube.onGetPlaylist";
    public static final String JSI_ON_GET_PLAYLIST_INDEX = "window.YouTube.onGetPlaylistIndex";

    private final Context mContext;

    public YoutubeJsInterface(Context context) {
        mContext = context;
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