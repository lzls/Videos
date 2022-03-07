/*
 * Created on 2022-3-2 4:43:29 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package androidx.appcompat.app;

public interface AppCompatDelegateExtensions {
    void onFinished();
    void onPictureInPictureModeChanged(boolean isInPictureInPictureMode);
}
