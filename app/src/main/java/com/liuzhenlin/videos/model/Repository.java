/*
 * Created on 2024-12-23 9:35:41 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model;

import androidx.annotation.Nullable;

public interface Repository<C extends Repository.Callback> {

    void setCallback(@Nullable C callback);

    interface Callback {
    }
}
