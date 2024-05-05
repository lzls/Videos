/*
 * Created on 2022-8-1 7:49:17 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.LibraryLoader;
import androidx.media3.common.util.UnstableApi;

/** Configures and queries the underlying native library. */
@OptIn(markerClass = UnstableApi.class)
public class SophixAppLibrary {

    private static final LibraryLoader LOADER =
            new LibraryLoader("sophix_app") {
                @Override
                protected void loadLibrary(@NonNull String name) {
                    System.loadLibrary(name);
                }
            };

    private SophixAppLibrary() {}

    /** Returns whether the underlying library is available, loading it if necessary. */
    public static boolean isAvailable() {
        return LOADER.isAvailable();
    }

    /** Throws if the underlying library is not loadable. */
    public static void throwIfNotAvailable() {
        if (!LOADER.isAvailable()) {
            throw new RuntimeException("Failed to load sophix_app native library.");
        }
    }
}
