/*
 * Created on 2022-1-3 11:22:26 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.LibraryLoader;
import androidx.media3.common.util.UnstableApi;

/** Configures and queries the underlying native library. */
@OptIn(markerClass = UnstableApi.class)
public final class VideosLibrary {

    private static final LibraryLoader LOADER =
            new LibraryLoader("videos") {
                @Override
                protected void loadLibrary(@NonNull String name) {
                    System.loadLibrary(name);
                }
            };

    private VideosLibrary() {}

    /** Returns whether the underlying library is available, loading it if necessary. */
    public static boolean isAvailable() {
        return LOADER.isAvailable();
    }

    /** Throws if the underlying library is not loadable. */
    public static void throwIfNotAvailable() {
        if (!LOADER.isAvailable()) {
            throw new RuntimeException("Failed to load videos native library.");
        }
    }
}

