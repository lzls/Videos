/*
 * Created on 2022-2-16 6:56:12 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.webkit.WebView;

import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.videos.web.player.WebPlayer;

import androidx.annotation.Nullable;

@NonNullApi
public class YoutubePlayer extends WebPlayer {

    public YoutubePlayer(WebView web) {
        super(web);
    }

    public void attachListeners() {
        mWeb.loadUrl(Youtube.JsInterface.attachListeners());
    }

    @Override
    public void loadVideo(String vid) {
        mWeb.loadUrl(Youtube.JsInterface.loadVideo(vid));
    }

    @Override
    public void loadPlaylist(String pid, @Nullable Object vid) {
        mWeb.loadUrl(Youtube.JsInterface.loadPlaylist(pid, vid == null ? "" : vid.toString()));
    }

    @Override
    public void skipAd() {
        mWeb.loadUrl(Youtube.JsInterface.skipAd());
    }

    @Override
    public void setMuted(boolean muted) {
        mWeb.loadUrl(Youtube.JsInterface.setMuted(muted));
    }

    @Override
    public void play() {
        mWeb.loadUrl(Youtube.JsInterface.playVideo());
    }

    @Override
    public void pause() {
        mWeb.loadUrl(Youtube.JsInterface.pauseVideo());
    }

    @Override
    public void stop() {
        mWeb.loadUrl(Youtube.JsInterface.stopVideo());
    }

    @Override
    public void prev() {
        mWeb.loadUrl(Youtube.JsInterface.prevVideo());
    }

    @Override
    public void next() {
        mWeb.loadUrl(Youtube.JsInterface.nextVideo());
    }

    @Override
    public void seekTo(long position) {
        mWeb.loadUrl(Youtube.JsInterface.seekTo(position));
    }

    @Override
    public void seekToDefault() {
        mWeb.loadUrl(Youtube.JsInterface.seekToDefault());
    }

    @Override
    public void fastForward() {
        mWeb.loadUrl(Youtube.JsInterface.fastForward());
    }

    @Override
    public void fastRewind() {
        mWeb.loadUrl(Youtube.JsInterface.fastRewind());
    }

    @Override
    public void setPlaybackQuality(Object qualityIndex) {
        mWeb.loadUrl(Youtube.JsInterface.setPlaybackQuality((int) qualityIndex));
    }

    @Override
    public void setLoopPlaylist(boolean loop) {
        mWeb.loadUrl(Youtube.JsInterface.setLoopPlaylist(loop));
    }

    @Override
    public void replayPlaylist() {
        mWeb.loadUrl(Youtube.JsInterface.replayPlaylist());
    }

    @Override
    public void playVideoAt(int index) {
        mWeb.loadUrl(Youtube.JsInterface.playVideoAt(index));
    }

    @Override
    public void requestGetPlaylist() {
        mWeb.loadUrl(Youtube.JsInterface.getPlaylist());
    }

    @Override
    public void requestGetPlaylistIndex() {
        mWeb.loadUrl(Youtube.JsInterface.getPlaylistIndex());
    }

    @Override
    public void requestGetVideoId() {
        mWeb.loadUrl(Youtube.JsInterface.getVideoId());
    }
}
