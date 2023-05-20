/*
 * Created on 2023-5-18 3:51:52 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity.startup;

import android.app.Activity;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

public class BaseProcessor<H extends Activity & LifecycleOwner> implements LaunchChain.Processor<H> {
    protected LaunchChain<H> mChain;

    @CallSuper
    @Override
    public boolean process(@NonNull LaunchChain<H> chain) {
        mChain = chain;
        return false;
    }
}
