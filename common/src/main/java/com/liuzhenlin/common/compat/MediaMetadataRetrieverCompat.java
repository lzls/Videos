/*
 * Created on 2023-5-18 11:14:07 AM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.compat;

import android.media.MediaMetadataRetriever;

import java.io.IOException;

import javax.annotation.Nullable;

public class MediaMetadataRetrieverCompat {
    private MediaMetadataRetrieverCompat() {
    }

    public static void release(@Nullable MediaMetadataRetriever mmr) {
        if (mmr != null) {
            try {
                mmr.release();
            } catch (IOException e) {
                //
            }
        }
    }
}
