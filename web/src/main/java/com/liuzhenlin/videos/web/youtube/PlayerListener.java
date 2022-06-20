/*
 * Created on 2022-6-20 8:50:40 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

public interface PlayerListener {

    default void onPlayerReady() {
    }

    default void onPlayerStateChange(@Youtube.PlayingStatus int status) {
    }
}
