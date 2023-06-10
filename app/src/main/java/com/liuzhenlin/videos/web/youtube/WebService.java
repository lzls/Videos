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
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.util.Consumer;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.LanguageUtils;
import com.liuzhenlin.common.utils.ServiceBindHelper;
import com.liuzhenlin.common.utils.Utils;

public class WebService extends Service {

    public static void bind(@NonNull Context context, @Nullable Consumer<IWebService> onBindAction) {
        ServiceBindHelper.bind(context, WebService.class,
                onBindAction == null ?
                        null : service -> onBindAction.accept(IWebService.Stub.asInterface(service)));
    }

    public static void unbind(@NonNull Context context) {
        ServiceBindHelper.unbind(context, WebService.class);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new IWebService.Stub() {
            @Override
            public void applyDefaultNightMode(int mode) throws RemoteException {
                Executors.MAIN_EXECUTOR.execute(() -> {
                    AppCompatDelegate.setDefaultNightMode(mode);
                    // YoutubePlaybackView will not be recreated according to our code
                    // as it was created in YoutubePlaybackService directly.
                    YoutubePlaybackService.peekIfNonnullThenDo(service -> {
                        WebView web = service.mView;
                        if (web != null
                                && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                            WebSettingsCompat.setForceDark(
                                    web.getSettings(), Utils.nightModeToWebSettingsForceDarkInt(mode));
                        }
                    });
                });
            }

            @Override
            public void applyDefaultLanguageMode(int mode) throws RemoteException {
                Executors.MAIN_EXECUTOR.execute(() -> {
                    // TODO: apply the new language mode onto YoutubePlaybackView
                    LanguageUtils.setDefaultLanguageMode(WebService.this, mode);
                });
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
