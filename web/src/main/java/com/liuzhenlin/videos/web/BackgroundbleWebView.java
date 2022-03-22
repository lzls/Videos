/*
 * Created on 2022-3-21 9:11:40 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.Synthetic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class BackgroundbleWebView extends AndroidWebView {

    private static final String PROVIDER_CLS_NAME = "android.webkit.WebViewProvider";

    private static Field sProviderField;
    private static Class<?> sProviderCls;
    @Synthetic static Class<?> sViewDelegateCls;

    static {
        try {
            //noinspection JavaReflectionMemberAccess,DiscouragedPrivateApi
            sProviderField = WebView.class.getDeclaredField("mProvider");
            sProviderField.setAccessible(true);

            //noinspection PrivateApi
            sProviderCls = Class.forName(PROVIDER_CLS_NAME);
            //noinspection PrivateApi
            sViewDelegateCls = Class.forName(PROVIDER_CLS_NAME + "$ViewDelegate");
        } catch (Exception e) {
            sProviderField = null;
            sViewDelegateCls = sProviderCls = null;
            e.printStackTrace();
        }
    }

    public BackgroundbleWebView(Context context) {
        super(context);
    }

    public BackgroundbleWebView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BackgroundbleWebView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void setup(@NonNull WebSettings settings) {
        super.setup(settings);
        if (sProviderField == null || sProviderCls == null || sViewDelegateCls == null) {
            return;
        }
        try {
            ClassLoader classLoader = mContext.getClassLoader();
            Object provider = sProviderField.get(this);
            Object providerProxy = Proxy.newProxyInstance(
                    classLoader,
                    new Class[]{ sProviderCls },
                    new InvocationHandler() {
                        Object mViewDelegateProxy;
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (method.getName().equals("getViewDelegate")) {
                                if (mViewDelegateProxy == null) {
                                    Object viewDelegate = method.invoke(provider, args);
                                    mViewDelegateProxy = Proxy.newProxyInstance(
                                            classLoader,
                                            new Class[]{ sViewDelegateCls },
                                            (proxy1, method1, args1) -> {
                                                switch (method1.getName()) {
                                                    case "onWindowVisibilityChanged":
                                                        if ((int) args1[0] != VISIBLE) {
                                                            return null;
                                                        }
                                                        break;
                                                    case "onVisibilityChanged":
                                                        if (args1[0] == BackgroundbleWebView.this
                                                                && (int) args1[1] != VISIBLE) {
                                                            return null;
                                                        }
                                                        break;
                                                    case "onDetachedFromWindow":
                                                        if (!shouldStopWhenDetachedFromWindow()) {
                                                            return null;
                                                        }
                                                        break;
                                                }
                                                return method1.invoke(viewDelegate, args1);
                                            });
                                }
                                return mViewDelegateProxy;
                            }
                            return method.invoke(provider, args);
                        }
                    });
            sProviderField.set(this, providerProxy);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected boolean shouldStopWhenDetachedFromWindow() {
        return true;
    }
}
