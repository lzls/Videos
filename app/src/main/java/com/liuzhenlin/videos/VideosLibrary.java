/*
 * Created on 2022-1-3 11:22:26 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos;

import com.google.android.exoplayer2.util.LibraryLoader;

/** Configures and queries the underlying native library. */
public final class VideosLibrary {

    private static final LibraryLoader LOADER = new LibraryLoader("videos");

    private VideosLibrary() {}

    /** Returns whether the underlying library is available, loading it if necessary. */
    public static boolean isAvailable() {
        return LOADER.isAvailable();
    }
}

