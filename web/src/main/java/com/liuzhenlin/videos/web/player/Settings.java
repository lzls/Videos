/*
 * Created on 2022-2-17 9:06:29 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.player;

public final class Settings {
    private Settings() {}

    private static boolean finishServiceOnPlaylistEnded = false;
    private static int repeatMode = Constants.RepeatMode.NONE;

    public static boolean shouldFinishServiceOnPlaylistEnded() {
        return finishServiceOnPlaylistEnded;
    }

    public static void setFinishServiceOnPlaylistEnded(boolean finishServiceOnPlaylistEnded) {
        Settings.finishServiceOnPlaylistEnded = finishServiceOnPlaylistEnded;
    }

    public static int getRepeatMode() {
        return repeatMode;
    }

    public static void setRepeatMode(int repeatMode) {
        Settings.repeatMode = repeatMode;
    }
}
