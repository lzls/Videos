/*
 * Created on 2022-3-2 5:22:45 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package androidx.appcompat.app;

import com.liuzhenlin.common.utils.ThemeUtils;

public class PlatformPendingTransitionOverrides implements PendingTransitionOverrides {
    @Override
    public int getOpenEnterTransition() {
        return ThemeUtils.getDefaultActivityOpenEnterAnim();
    }

    @Override
    public int getOpenExitTransition() {
        return ThemeUtils.getDefaultActivityOpenExitAnim();
    }

    @Override
    public int getCloseEnterTransition() {
        return ThemeUtils.getDefaultActivityCloseEnterAnim();
    }

    @Override
    public int getCloseExitTransition() {
        return ThemeUtils.getDefaultActivityCloseExitAnim();
    }
}
