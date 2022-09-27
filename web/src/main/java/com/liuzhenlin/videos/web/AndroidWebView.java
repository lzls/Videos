/*
 * Created on 2022-2-26 4:59:37 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.webkit.WebViewClientCompat;

import com.liuzhenlin.common.Configs;
import com.liuzhenlin.common.utils.ListenerSet;
import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.common.utils.Regex;

import static java.util.Objects.requireNonNull;

@NonNullApi
public class AndroidWebView extends WebView {

    protected final Context mContext;

    protected final ListenerSet<PageListener> mPageListeners = new ListenerSet<>();

    public AndroidWebView(Context context) {
        this(context, null);
    }

    public AndroidWebView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public AndroidWebView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        init();
    }

    private void init() {
        setup(getSettings());

        // So that we can catch the back button
//        setFocusableInTouchMode(true);
    }

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        if (keyCode == KeyEvent.KEYCODE_BACK && canGoBack()) {
//            event.startTracking();
//            return true;
//        }
//        return super.onKeyDown(keyCode, event);
//    }

//    @Override
//    public boolean onKeyUp(int keyCode, KeyEvent event) {
//        if (keyCode == KeyEvent.KEYCODE_BACK) {
//            if (canGoBack()) {
//                goBack();
//                return true;
//            }
//        }
//        return super.onKeyUp(keyCode, event);
//    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean consumed = super.onTouchEvent(event);
        if (consumed && event.getAction() == MotionEvent.ACTION_DOWN
                && getScrollY() == 0) {
            // Avoids touch conflicts between this view and SwipeRefreshLayout, where the scrollY
            // is 0 but this view is actually scrolling a child panel up.
            setScrollY(1);
        }
        return consumed;
    }

    protected void setup(WebSettings settings) {
        // 如果访问的页面中要与Javascript交互，则WebView必须设置支持JavaScript
        settings.setJavaScriptEnabled(true);
        // 若加载的 html 里有 JS 在执行动画等操作，会造成资源浪费（CPU、电量）
        // 在 onStop 和 onResume 里分别把 setJavaScriptEnabled() 给设置成 false 和 true 即可

        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // 设置自适应屏幕，两者合用
        settings.setUseWideViewPort(true); // 将图片调整到适合WebView的大小
        settings.setLoadWithOverviewMode(true); // 缩放至屏幕的大小

        settings.setLoadsImagesAutomatically(true);
        settings.setDefaultTextEncodingName(Configs.DEFAULT_CHARSET);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setWebContentsDebuggingEnabled(com.liuzhenlin.videos.web.Configs.WEB_DEBUGGING_ENABLED);
        }

        setWebViewClient(createWebViewClient());
        setWebChromeClient(createWebChromeClient());
    }

    protected ChromeClient createWebChromeClient() {
        return new ChromeClient();
    }

    protected Client createWebViewClient() {
        return new Client();
    }

    public boolean isInFullscreen() {
        return false;
    }

    public boolean canEnterFullscreen() {
        return true;
    }

    public boolean canExitFullscreen() {
        return false;
    }

    public void exitFullscreen() {
    }

    public void enterFullscreen() {
    }

    public void addPageListener(@Nullable PageListener listener) {
        mPageListeners.add(listener);
    }

    public void removePageListener(@Nullable PageListener listener) {
        mPageListeners.remove(listener);
    }

    public interface PageListener {

        default void onPageStarted(WebView view, String url, @Nullable Bitmap favicon) {}
        default void onPageFinished(WebView view, String url) {}

        default void onLoadingProgressChanged(WebView view, int newProgress) {}

        default void onEnterFullscreen(WebView view) {}
        default void onExitFullscreen(WebView view) {}
    }

    public class Client extends WebViewClientCompat {
        @Override
        public void onPageStarted(WebView view, String url, @Nullable Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            mPageListeners.forEach(listener -> listener.onPageStarted(view, url, favicon));
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mPageListeners.forEach(listener -> listener.onPageFinished(view, url));
        }
    }

    public class ChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            mPageListeners.forEach(
                    listener -> listener.onLoadingProgressChanged(view, newProgress));
        }
    }

    // Copied from Fermata
    public static final class UserAgent {
        private UserAgent() {}

        private static final String REGEX = ".+ AppleWebKit/(\\S+) .+ Chrome/(\\S+) .+";
        @Nullable static String ua;
        @Nullable static String uaDesktop;

        private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android {ANDROID_VERSION}) "
                + "AppleWebKit/{WEBKIT_VERSION} (KHTML, like Gecko) "
                + "Chrome/{CHROME_VERSION} Mobile Safari/{WEBKIT_VERSION}";
        private static final String USER_AGENT_DESKTOP = "Mozilla/5.0 (X11; Linux x86_64) "
                + "AppleWebKit/{WEBKIT_VERSION} (KHTML, like Gecko) "
                + "Chrome/{CHROME_VERSION} Safari/{WEBKIT_VERSION}";

        public static String getUa(WebSettings s) {
            if (ua != null) return ua;

            String ua = s.getUserAgentString();
            Regex regex = new Regex(REGEX);
            if (regex.matches(ua)) {
                String av;
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                    av = Build.VERSION.RELEASE_OR_CODENAME;
//                } else {
                    av = Build.VERSION.RELEASE;
//                }
                String wv = requireNonNull(regex.group(1));
                String cv = requireNonNull(regex.group(2));
                UserAgent.ua = USER_AGENT
                        .replace("{ANDROID_VERSION}", av)
                        .replace("{WEBKIT_VERSION}", wv)
                        .replace("{CHROME_VERSION}", cv);
                UserAgent.ua = normalize(UserAgent.ua);
                if (UserAgent.ua.isEmpty()) UserAgent.ua = ua;
            } else {
//                Log.w("User-Agent does not match the regex ", regex, ": " + ua);
                UserAgent.ua = ua;
            }

            return UserAgent.ua;
        }

        public static String getUaDesktop(WebSettings s) {
            if (uaDesktop != null) return uaDesktop;

            String ua = s.getUserAgentString();
            Regex regex = new Regex(REGEX);
            if (regex.matches(ua)) {
                String wv = requireNonNull(regex.group(1));
                String cv = requireNonNull(regex.group(2));
                uaDesktop = USER_AGENT_DESKTOP
                        .replace("{WEBKIT_VERSION}", wv)
                        .replace("{CHROME_VERSION}", cv);
            } else {
//                Log.w("User-Agent does not match the regex ", regex, ": " + ua);
                int i1 = ua.indexOf('(') + 1;
                int i2 = ua.indexOf(')', i1);
                uaDesktop = ua.substring(0, i1) + "X11; Linux x86_64" + ua.substring(i2)
                        .replace(" Mobile ", " ")
                        .replaceFirst(" Version/\\d+\\.\\d+ ", " ");
            }

            return uaDesktop = normalize(uaDesktop);
        }

        private static String normalize(String ua) {
            int cut = 0;
            boolean changed = false;
            StringBuilder b = new StringBuilder();

            for (int i = 0, n = ua.length(); i < n; i++) {
                char c = ua.charAt(i);

                if (c <= ' ') {
                    if ((b.length() == 0) || (ua.charAt(i - 1) == ' ')) {
                        changed = true;
                        continue;
                    } else if (c != ' ') {
                        b.append(' ');
                        changed = true;
                        continue;
                    }
                }

                b.append(c);
            }

            for (int i = b.length() - 1; i >= 0; i--) {
                if (b.charAt(i) == ' ') cut++;
                else break;
            }

            if (cut != 0) {
                changed = true;
                b.setLength(b.length() - cut);
            }

            return changed ? b.toString() : ua;
        }
    }
}
