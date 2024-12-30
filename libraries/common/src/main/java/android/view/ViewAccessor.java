/*
 * Created on 2022-11-26 4:45:17 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package android.view;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

public class ViewAccessor {
    private ViewAccessor() {
    }

    public static boolean verifyDrawable(@NonNull View view, @NonNull Drawable who) {
        return view.verifyDrawable(who);
    }
}
