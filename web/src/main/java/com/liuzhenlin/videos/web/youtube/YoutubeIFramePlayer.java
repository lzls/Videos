/*
 * Created on 2022-2-16 6:56:12 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.videos.web.player.WebPlayer;

@NonNullApi
public class YoutubeIFramePlayer extends WebPlayer {

    public YoutubeIFramePlayer(WebView web) {
        super(web);
    }

    @Override
    public void loadVideo(String vid) {
        mWeb.loadUrl(Youtube.IFrameJsInterface.loadVideo(vid));
    }

    @Override
    public void loadPlaylist(String pid, @Nullable String vid, int index) {
        mWeb.loadUrl(Youtube.IFrameJsInterface.loadPlaylist(pid, index));
    }

    @Override
    public void skipAd() {
    }

    @Override
    public void setMuted(boolean muted) {
        mWeb.loadUrl(Youtube.IFrameJsInterface.setMuted(muted));
    }

    @Override
    public void play() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.playVideo());
    }

    @Override
    public void pause() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.pauseVideo());
    }

    @Override
    public void stop() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.stopVideo());
    }

    @Override
    public void prev() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.prevVideo());
    }

    @Override
    public void next() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.nextVideo());
    }

    @Override
    public void seekTo(long position) {
        mWeb.loadUrl(Youtube.IFrameJsInterface.seekTo(position));
    }

    @Override
    public void seekToDefault() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.seekToDefault());
    }

    @Override
    public void fastForward() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.fastForward());
    }

    @Override
    public void fastRewind() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.fastRewind());
    }

    @Override
    public void setPlaybackQuality(Object quality) {
        mWeb.loadUrl(Youtube.IFrameJsInterface.setPlaybackQuality(quality.toString()));
    }

    @Override
    public void setLoopPlaylist(boolean loop) {
        mWeb.loadUrl(Youtube.IFrameJsInterface.setLoopPlaylist(loop));
    }

    @Override
    public void replayPlaylist() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.replayPlaylist());
    }

    @Override
    public void playVideoAt(int index) {
        mWeb.loadUrl(Youtube.IFrameJsInterface.playVideoAt(index));
    }

    @Override
    public void requestGetPlaylist() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.getPlaylist());
    }

    @Override
    public void requestGetPlaylistIndex() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.getPlaylistIndex());
    }

    @Override
    public void requestGetVideoId() {
        mWeb.loadUrl(Youtube.IFrameJsInterface.getVideoId());
    }
}
