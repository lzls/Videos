/*
 * Created on 2022-2-16 6:56:12 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.player;

import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.NonNullApi;

@NonNullApi
public abstract class WebPlayer {

    protected final WebView mWeb;

    public WebPlayer(WebView web) {
        mWeb = web;
    }

    public abstract void loadVideo(String vid);

    public abstract void loadPlaylist(String pid, @Nullable String vid, int index);

    public abstract void skipAd();

    public abstract void setMuted(boolean muted);

    public abstract void play();

    public abstract void pause();

    public abstract void stop();

    public abstract void prev();

    public abstract void next();

    public abstract void seekTo(long position);

    public abstract void seekToDefault();

    public abstract void fastForward();

    public abstract void fastRewind();

    public abstract void setPlaybackQuality(Object arg);

    public abstract void setLoopPlaylist(boolean loop);

    public abstract void replayPlaylist();

    public abstract void playVideoAt(int index);

    public abstract void requestGetPlaylist();

    public abstract void requestGetPlaylistIndex();

    public abstract void requestGetVideoId();
}
