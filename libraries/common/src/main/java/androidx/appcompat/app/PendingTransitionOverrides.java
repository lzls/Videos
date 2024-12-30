/*
 * Created on 2022-3-2 4:26:36 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package androidx.appcompat.app;

import androidx.annotation.AnimRes;

public interface PendingTransitionOverrides {
    @AnimRes int getOpenEnterTransition();
    @AnimRes int getOpenExitTransition();
    @AnimRes int getCloseEnterTransition();
    @AnimRes int getCloseExitTransition();
}
