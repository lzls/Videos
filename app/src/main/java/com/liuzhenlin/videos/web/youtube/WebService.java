/*
 * Created on 2022-3-5 12:07:30 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.util.Consumer;

import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.ServiceBindHelper;

public class WebService extends Service {

    public static void bind(@NonNull Context context, @NonNull Consumer<IWebService> onBindAction) {
        ServiceBindHelper.bind(context, WebService.class,
                service -> onBindAction.accept(IWebService.Stub.asInterface(service)));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new IWebService.Stub() {
            @Override
            public void applyDefaultNightMode(int mode) throws RemoteException {
                Executors.MAIN_EXECUTOR.execute(() -> AppCompatDelegate.setDefaultNightMode(mode));
            }

            @Override
            public void finishYoutubePlaybackActivityIfItIsInPiP() throws RemoteException {
                Executors.MAIN_EXECUTOR.execute(() -> {
                    YoutubePlaybackActivity ytPlaybackActivity = YoutubePlaybackActivity.get();
                    if (ytPlaybackActivity != null && ytPlaybackActivity.isInPictureInPictureMode()) {
                        ytPlaybackActivity.finish();
                    }
                });
            }
        };
    }
}
