/*
 * Created on 2022-4-4 6:47:05 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.videos.web.player.WebPlayer;

import java.util.WeakHashMap;

@NonNullApi
public class YoutubePlayerFactory {

    private static final WeakHashMap<WebView, WebPlayer> sPlayers = new WeakHashMap<>();

    /**
     * Obtains a WebPlayer used to play YouTube videos on the given WebView based on
     * the current user's preferred UI style for the playback page.
     */
    public static WebPlayer obtain(WebView webView) {
        @Nullable WebPlayer player = sPlayers.get(webView);
        switch (Youtube.Prefs.get(webView.getContext()).getPlaybackPageStyle()) {
            case Youtube.Prefs.PLAYBACK_PAGE_STYLE_ORIGINAL:
                if (!(player instanceof YoutubePlayer)) {
                    player = new YoutubePlayer(webView);
                    sPlayers.put(webView, player);
                }
                return player;
            case Youtube.Prefs.PLAYBACK_PAGE_STYLE_BRIEF:
                if (!(player instanceof YoutubeIFramePlayer)) {
                    player = new YoutubeIFramePlayer(webView);
                    sPlayers.put(webView, player);
                }
                return player;
            default:
                throw new IllegalStateException(
                        "Unknown style preferred for YouTube playback page is provided.");
        }
    }
}
