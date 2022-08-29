/*
 * Created on 2020-3-30 6:44:29 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.collection.ArrayMap;
import androidx.core.app.NotificationCompat;
import androidx.core.util.ObjectsCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.bumptech.glide.util.Synthetic;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liuzhenlin.common.compat.FragmentManagerCompat;
import com.liuzhenlin.common.utils.ActivityUtils;
import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.FileUtils;
import com.liuzhenlin.common.utils.IOUtils;
import com.liuzhenlin.common.utils.NotificationChannelManager;
import com.liuzhenlin.common.utils.Singleton;
import com.liuzhenlin.common.utils.TextViewUtils;
import com.liuzhenlin.videos.BuildConfig;
import com.liuzhenlin.videos.Consts;
import com.liuzhenlin.videos.Configs;
import com.liuzhenlin.videos.ExtentionsKt;
import com.liuzhenlin.videos.Files;
import com.liuzhenlin.videos.R;

import org.apache.http.conn.ConnectTimeoutException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.collections.ArraysKt;

import static com.liuzhenlin.common.utils.Utils.hasNotification;
import static com.liuzhenlin.common.utils.Utils.postTillConditionMeets;

/**
 * @author 刘振林
 */
@MainThread
public final class MergeAppUpdateChecker {

    public interface OnResultListener {
        void onResult(boolean findNewVersion);
    }

    private static final String TAG = "AppUpdateChecker";

    private static final int TIMEOUT_CONNECTION = 10 * 1000; // ms
    private static final int TIMEOUT_READ = 30 * 1000; // ms

    private static final String LINK_APP_INFOS =
            "https://gitlab.com/lzls/Videos-Server/-/raw/master/app/Android/app.json";

    @Synthetic String mAppName;
    @Synthetic String mVersionName;
    @Synthetic String[] mAppPartLinks;
    @Synthetic int mAppLength;
    @Synthetic String mAppSha1;
    @Synthetic StringBuilder mUpdateLog;
    @Synthetic String mPromptDialogAnchorActivityClsName;
    @Synthetic boolean mNewVersionFound;

    @Synthetic final Context mContext;
    @Synthetic final H mH;
    @Synthetic boolean mToastResult;
    private boolean mCheckInProgress;

    @Synthetic List<OnResultListener> mListeners;

    private static final String EXTRA_APP_NAME = "appName";
    private static final String EXTRA_VERSION_NAME = "versionName";
    private static final String EXTRA_APP_PART_LINKS = "appPartLinks";
    private static final String EXTRA_APP_LENGTH = "appLength";
    private static final String EXTRA_APP_SHA1 = "appSha1";
    @Synthetic Intent mServiceIntent;

    private static final Singleton<Context, MergeAppUpdateChecker> sMergeAppUpdateCheckerSingleton =
            new Singleton<Context, MergeAppUpdateChecker>() {
                @SuppressLint("SyntheticAccessor")
                @NonNull
                @Override
                protected MergeAppUpdateChecker onCreate(Context... ctxs) {
                    return new MergeAppUpdateChecker(ctxs[0]);
                }
            };

    @NonNull
    public static MergeAppUpdateChecker getSingleton(@NonNull Context context) {
        return sMergeAppUpdateCheckerSingleton.get(context);
    }

    private MergeAppUpdateChecker(Context context) {
        mContext = context.getApplicationContext();
        mH = new H();
    }

    @Synthetic boolean hasOnResultListener() {
        return mListeners != null && !mListeners.isEmpty();
    }

    // 注：添加后请记得移除，以免引起内存泄漏
    public void addOnResultListener(@Nullable OnResultListener listener) {
        if (listener != null) {
            if (mListeners == null) {
                mListeners = new ArrayList<>(1);
            }
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
    }

    public void removeOnResultListener(@Nullable OnResultListener listener) {
        if (listener != null && hasOnResultListener()) {
            mListeners.remove(listener);
        }
    }

    public void checkUpdate() {
        checkUpdate(true);
    }

    @SuppressLint("StaticFieldLeak")
    public void checkUpdate(boolean toastResult) {
        mToastResult = toastResult;

        if (mCheckInProgress) return;
        mCheckInProgress = true;
        new AsyncTask<Void, Void, Integer>() {
            static final int RESULT_FIND_NEW_VERSION = 1;
            static final int RESULT_NO_NEW_VERSION = 2;
            static final int RESULT_CONNECTION_TIMEOUT = 3;
            static final int RESULT_READ_TIMEOUT = 4;

            @Override
            protected Integer doInBackground(Void... voids) {
                String json;

                HttpURLConnection conn = null;
                try {
                    URL url = new URL(LINK_APP_INFOS);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(TIMEOUT_CONNECTION);
                    conn.setReadTimeout(TIMEOUT_READ);

                    json = IOUtils.decodeStringFromStream(conn.getInputStream());

                    // 连接服务器超时
                } catch (ConnectTimeoutException e) {
                    Log.w(TAG, e);
                    return RESULT_CONNECTION_TIMEOUT;

                    // 读取数据超时
                } catch (SocketTimeoutException e) {
                    Log.w(TAG, e);
                    return RESULT_READ_TIMEOUT;

                } catch (IOException e) {
                    Log.w(TAG, e);
                    return 0;

                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }

                //noinspection ConstantConditions
                JsonObject appInfos = JsonParser.parseString(json).getAsJsonObject()
                        .get("appInfos").getAsJsonObject();

                final boolean findNewVersion = Configs.DEBUG_APP_UPDATE
                        || appInfos.get("versionCode").getAsInt() > BuildConfig.VERSION_CODE;
                // 检测到版本更新
                if (findNewVersion) {
                    mAppName = appInfos.get("appName").getAsString();
                    mVersionName = appInfos.get("versionName").getAsString();

                    JsonArray links = appInfos.get("appPartLinks").getAsJsonArray();
                    mAppPartLinks = new String[links.size()];
                    for (int i = 0; i < mAppPartLinks.length; i++) {
                        mAppPartLinks[i] = links.get(i).getAsString();
                    }

                    mAppLength = appInfos.get("appLength").getAsInt();
                    mAppSha1 = appInfos.get("appSha1").getAsString();

                    mUpdateLog = new StringBuilder();
                    for (JsonElement log : appInfos.get("updateLogs").getAsJsonArray()) {
                        mUpdateLog.append(log.getAsString()).append("\n");
                    }
                    mUpdateLog.deleteCharAt(mUpdateLog.length() - 1);

                    mPromptDialogAnchorActivityClsName =
                            appInfos.get("promptDialogAnchorActivityClsName").getAsString();
                }

                mNewVersionFound = findNewVersion;
                return findNewVersion ? RESULT_FIND_NEW_VERSION : RESULT_NO_NEW_VERSION;
            }

            @Override
            protected void onPostExecute(Integer result) {
                switch (result) {
                    case RESULT_FIND_NEW_VERSION:
                        mH.sendEmptyMessage(H.MSG_FIND_NEW_VERSION);
                        showUpdatePromptDialog();
                        break;
                    case RESULT_NO_NEW_VERSION:
                        mH.sendEmptyMessage(H.MSG_NO_NEW_VERSION);
                        if (mToastResult) {
                            Toast.makeText(mContext,
                                    R.string.isTheLatestVersion, Toast.LENGTH_SHORT).show();
                        }
                        reset();
                        break;
                    case RESULT_CONNECTION_TIMEOUT:
                        if (mToastResult) {
                            Toast.makeText(mContext,
                                    R.string.connectionTimeout, Toast.LENGTH_SHORT).show();
                        }
                        reset();
                        break;
                    case RESULT_READ_TIMEOUT:
                        if (mToastResult) {
                            Toast.makeText(mContext,
                                    R.string.readTimeout, Toast.LENGTH_SHORT).show();
                        }
                    default:
                        reset();
                        break;
                }
            }
        }.executeOnExecutor(Executors.THREAD_POOL_EXECUTOR);
    }

    /**
     * 弹出对话框，提醒用户更新
     */
    @Synthetic void showUpdatePromptDialog() {
        Activity anchorActivity = ActivityUtils.getActivityForName(mPromptDialogAnchorActivityClsName);
        if (anchorActivity == null || anchorActivity.isFinishing()) {
            Log.w(TAG, "The Activity in which the dialog should run doesn't exist, " +
                    "so it will not be showed at all and this check finishes.");
            reset();
            return;
        }

        UpdatePromptDialogFragmentHelper.showFragmentOn(anchorActivity);
    }

    @Synthetic void reset() {
        mAppName = null;
        mVersionName = null;
        mAppPartLinks = null;
        mAppLength = 0;
        mAppSha1 = null;
        mUpdateLog = null;
        mPromptDialogAnchorActivityClsName = null;
        mNewVersionFound = false;
        mCheckInProgress = false;
        mServiceIntent = null;
    }

    @SuppressLint("HandlerLeak")
    private final class H extends Handler {
        static final int MSG_STOP_UPDATE_APP_SERVICE = -1;
        static final int MSG_NO_NEW_VERSION = 0;
        static final int MSG_FIND_NEW_VERSION = 1;
        static final int MSG_REMOVE_FRAGMENT_MANAGERS_PENDING_ADD = 2;
        static final int MSG_REMOVE_SUPPORT_FRAGMENT_MANAGERS_PENDING_ADD = 3;

        H() {
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            final int what = msg.what;
            switch (what) {
                case MSG_STOP_UPDATE_APP_SERVICE:
                    if (mServiceIntent != null) {
                        mContext.stopService(mServiceIntent);
                        reset();
                    }
                    break;
                case MSG_NO_NEW_VERSION:
                case MSG_FIND_NEW_VERSION:
                    if (hasOnResultListener()) {
                        for (int i = mListeners.size() - 1; i >= 0; i--) {
                            mListeners.get(i).onResult(what != MSG_NO_NEW_VERSION);
                        }
                    }
                    break;
                case MSG_REMOVE_FRAGMENT_MANAGERS_PENDING_ADD: {
                    android.app.FragmentManager fm = (android.app.FragmentManager) msg.obj;
                    boolean hasAttemptedBefore = msg.arg1 ==
                            UpdatePromptDialogFragmentHelper.HAS_ATTEMPTED_TO_ADD_FRAGMENT_TWICE;
                    UpdatePromptDialogFragmentHelper.removeFragmentManagersPendingAdd(
                            fm, hasAttemptedBefore);
                    break;
                }
                case MSG_REMOVE_SUPPORT_FRAGMENT_MANAGERS_PENDING_ADD: {
                    FragmentManager fm = (FragmentManager) msg.obj;
                    boolean hasAttemptedBefore = msg.arg1 ==
                            UpdatePromptDialogFragmentHelper.HAS_ATTEMPTED_TO_ADD_FRAGMENT_TWICE;
                    UpdatePromptDialogFragmentHelper.removeSupportFragmentManagersPendingAdd(
                            fm, hasAttemptedBefore);
                    break;
                }
            }
            super.handleMessage(msg);
        }
    }

    private interface IUpdatePromptDialogFragment {

        default void onCreate(@Nullable Bundle savedInstanceState) {
            if (savedInstanceState != null
                    && !MergeAppUpdateChecker.getSingleton(getThemedContext()).mNewVersionFound) {
                // This Fragment is being recreated during state restoration and no new app version
                // has been found due to app info maybe not fetched so far in the background.
                // So remove it to ensure no dialog to be unexpectedly created later.
                setShowsDialog(false);
                dismissAllowingStateLoss();
            }
        }

        @NonNull
        default Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            Context themedContext = getThemedContext();
            MergeAppUpdateChecker auc = MergeAppUpdateChecker.getSingleton(themedContext);
            Dialog dialog = new AppCompatDialog(themedContext, R.style.DialogStyle_MinWidth_NoTitle);
            View view = View.inflate(themedContext, R.layout.dialog_app_update_prompt, null);

            view.<TextView>findViewById(R.id.text_updateLogTitle)
                    .setText(getString(R.string.updateLog, auc.mAppName, auc.mVersionName));

            TextView tv = view.findViewById(R.id.text_updateLog);
            tv.setText(auc.mUpdateLog);
            TextViewUtils.setHangingIndents(tv);

            View.OnClickListener listener = v -> {
                switch (v.getId()) {
                    // 当点确定按钮时从服务器上下载新的apk，然后安装
                    case R.id.btn_confirm:
                        if (FileUtils.isExternalStorageMounted()) {
                            if (FileUtils.hasEnoughStorageOnDisk(auc.mAppLength)) {
                                auc.mServiceIntent = new Intent(auc.mContext, UpdateAppService.class)
                                        .putExtra(EXTRA_APP_NAME, auc.mAppName)
                                        .putExtra(EXTRA_VERSION_NAME, auc.mVersionName)
                                        .putExtra(EXTRA_APP_PART_LINKS, auc.mAppPartLinks)
                                        .putExtra(EXTRA_APP_LENGTH, auc.mAppLength)
                                        .putExtra(EXTRA_APP_SHA1, auc.mAppSha1);
                                auc.mContext.startService(auc.mServiceIntent);
                            } else {
                                auc.reset();
                                Toast.makeText(auc.mContext, R.string.notHaveEnoughStorage,
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            auc.reset();
                            Toast.makeText(auc.mContext, R.string.pleaseInsertSdCardOnYourDeviceFirst,
                                    Toast.LENGTH_SHORT).show();
                        }
                        dismissAllowingStateLoss();
                        break;
                    // 当点取消按钮时不做任何举动
                    case R.id.btn_cancel:
                        auc.reset();
                        dismissAllowingStateLoss();
                        break;
                }
            };
            view.findViewById(R.id.btn_cancel).setOnClickListener(listener);
            view.findViewById(R.id.btn_confirm).setOnClickListener(listener);

//            dialog.getWindow().setType(
//                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
//                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
//                            : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.setContentView(view);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            return dialog;
        }

        Context getThemedContext();

        String getString(@StringRes int resId, Object... formatArgs);

        void setShowsDialog(boolean showsDialog);

        void dismissAllowingStateLoss();

        default void onDismiss(@NonNull DialogInterface dialog) {
            // Resets MergeAppUpdateChecker in case the dialog was accidentally dismissed, e.g.,
            // Activity recreation, so as not to block the next update check request and causing
            // serious unresponsiveness issue.
            MergeAppUpdateChecker auc = MergeAppUpdateChecker.getSingleton(getThemedContext());
            if (auc.mServiceIntent == null) {
                auc.reset();
            }
        }
    }

    @SuppressWarnings("deprecation")
    @VisibleForTesting
    public static final class UpdatePromptDialogFragment extends DialogFragment
            implements IUpdatePromptDialogFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            IUpdatePromptDialogFragment.super.onCreate(savedInstanceState);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            return IUpdatePromptDialogFragment.super.onCreateDialog(savedInstanceState);
        }

        @Override
        public void onDismiss(@NonNull DialogInterface dialog) {
            super.onDismiss(dialog);
            IUpdatePromptDialogFragment.super.onDismiss(dialog);
        }

        @Override
        public Context getThemedContext() {
            Context ctx = getActivity();
            if (ctx == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ctx = getContext();
            }
            return ctx;
        }
    }

    @VisibleForTesting
    public static final class SupportUpdatePromptDialogFragment extends AppCompatDialogFragment
            implements IUpdatePromptDialogFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            IUpdatePromptDialogFragment.super.onCreate(savedInstanceState);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            return IUpdatePromptDialogFragment.super.onCreateDialog(savedInstanceState);
        }

        @Override
        public void onDismiss(@NonNull DialogInterface dialog) {
            super.onDismiss(dialog);
            IUpdatePromptDialogFragment.super.onDismiss(dialog);
        }

        @Override
        public Context getThemedContext() {
            return ExtentionsKt.getContextThemedFirst(this);
        }
    }

    private static final class UpdatePromptDialogFragmentHelper {

        static void showFragmentOn(Activity activity) {
            MergeAppUpdateChecker auc = MergeAppUpdateChecker.getSingleton(activity);
            // If we have a pending Fragment, we need to continue to use the pending Fragment.
            // Otherwise there's a race where an old Fragment could be added and retrieved here
            // before our logic to add our pending Fragment notices. That can then result in
            // both the pending Fragment and the old Fragment having dialogs running for them,
            // which is impossible to safely unwind.
            if (activity instanceof FragmentActivity) {
                FragmentManager fm = ((FragmentActivity) activity).getSupportFragmentManager();
                SupportUpdatePromptDialogFragment f =
                        sPendingSupportUpdatePromptDialogFragments.get(fm);
                if (f == null) {
                    f = (SupportUpdatePromptDialogFragment) fm.findFragmentByTag(FRAGMENT_TAG);
                    if (f == null) {
                        f = new SupportUpdatePromptDialogFragment();
                        fm.beginTransaction()
                                .add(f, FRAGMENT_TAG)
                                .commitAllowingStateLoss();
                        sPendingSupportUpdatePromptDialogFragments.put(fm, f);
                        auc.mH.obtainMessage(H.MSG_REMOVE_SUPPORT_FRAGMENT_MANAGERS_PENDING_ADD, fm)
                                .sendToTarget();
                    }
                }
            } else {
                android.app.FragmentManager fm = activity.getFragmentManager();
                UpdatePromptDialogFragment f = sPendingUpdatePromptDialogFragments.get(fm);
                if (f == null) {
                    f = (UpdatePromptDialogFragment) fm.findFragmentByTag(FRAGMENT_TAG);
                    if (f == null) {
                        f = new UpdatePromptDialogFragment();
                        fm.beginTransaction()
                                .add(f, FRAGMENT_TAG)
                                .commitAllowingStateLoss();
                        sPendingUpdatePromptDialogFragments.put(fm, f);
                        auc.mH.obtainMessage(H.MSG_REMOVE_FRAGMENT_MANAGERS_PENDING_ADD, fm)
                                .sendToTarget();
                    }
                }
            }
        }

        private static final String FRAGMENT_TAG = UpdatePromptDialogFragment.class.getName();

        /** Pending adds for UpdatePromptDialogFragment. */
        private static final Map<android.app.FragmentManager, UpdatePromptDialogFragment>
                sPendingUpdatePromptDialogFragments = new ArrayMap<>();

        /** Pending adds for SupportUpdatePromptDialogFragment. */
        private static final Map<FragmentManager, SupportUpdatePromptDialogFragment>
                sPendingSupportUpdatePromptDialogFragments = new ArrayMap<>();

        // Indicates that we've tried to add a UpdatePromptDialogFragment twice previously and is
        // used as a signal to give up and tear down the fragment.
        static final int HAS_ATTEMPTED_TO_ADD_FRAGMENT_TWICE = 1;

        static void removeFragmentManagersPendingAdd(
                android.app.FragmentManager fm, boolean hasAttemptedBefore) {
            if (verifyOurFragmentWasAddedOrCantBeAdded(fm, hasAttemptedBefore)) {
                Object removed = sPendingUpdatePromptDialogFragments.remove(fm);
                if (removed == null) {
                    Log.w(TAG, "Failed to remove expected update prompt dialog fragment," +
                            " manager: " + fm);
                }
            }
        }

        static void removeSupportFragmentManagersPendingAdd(
                FragmentManager fm, boolean hasAttemptedBefore) {
            if (verifyOurSupportFragmentWasAddedOrCantBeAdded(fm, hasAttemptedBefore)) {
                Object removed = sPendingSupportUpdatePromptDialogFragments.remove(fm);
                if (removed == null) {
                    Log.w(TAG, "Failed to remove expected update prompt dialog fragment," +
                            " manager: " + fm);
                }
            }
        }

        // We care about the instance specifically.
        @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
        private static boolean verifyOurFragmentWasAddedOrCantBeAdded(
                android.app.FragmentManager fm, boolean hasAttemptedToAddFragmentTwice) {
            UpdatePromptDialogFragment newlyAddedFragment =
                    sPendingUpdatePromptDialogFragments.get(fm);

            UpdatePromptDialogFragment actualFragment =
                    (UpdatePromptDialogFragment) fm.findFragmentByTag(FRAGMENT_TAG);
            if (actualFragment == newlyAddedFragment) {
                return true;
            }

            if (actualFragment != null) {
                throw new IllegalStateException(
                        "We've added two fragments!"
                                + " Old: " + actualFragment + " New: " + newlyAddedFragment);
            }

            // If our parent was destroyed, we're never going to be able to add our fragment, so we
            // should just clean it up and abort.
            // Similarly if we've already tried to add the fragment, waited a frame, then tried to
            // add the fragment a second time and still the fragment isn't present, we're unlikely
            // to be able to do so if we retry a third time. This is easy to reproduce in Robolectric
            // by obtaining an Activity but not creating it. If we continue to loop forever,
            // we break tests and, if it happens in the real world, might leak memory and waste
            // a bunch of CPU/battery.
            boolean fmDestroyed = FragmentManagerCompat.isDestroyed(fm);
            if (hasAttemptedToAddFragmentTwice || fmDestroyed) {
                if (fmDestroyed) {
                    Log.w(TAG, "Parent was destroyed before our Fragment could be added");
                } else {
                    Log.e(TAG, "Tried adding Fragment twice and failed twice, giving up!");
                }
                return true;
            }

            // Otherwise we should make another attempt to commit the fragment and loop back again
            // in the next frame to verify.
            fm.beginTransaction().add(newlyAddedFragment, FRAGMENT_TAG).commitAllowingStateLoss();
            MergeAppUpdateChecker.getSingleton(newlyAddedFragment.getThemedContext()).mH
                    .obtainMessage(
                            H.MSG_REMOVE_FRAGMENT_MANAGERS_PENDING_ADD,
                            HAS_ATTEMPTED_TO_ADD_FRAGMENT_TWICE, /* arg2= */ 0, fm)
                    .sendToTarget();
            Log.d(TAG, "We failed to add our Fragment the first time around, trying again...");
            return false;
        }

        // We care about the instance specifically.
        @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
        private static boolean verifyOurSupportFragmentWasAddedOrCantBeAdded(
                FragmentManager fm, boolean hasAttemptedToAddFragmentTwice) {
            SupportUpdatePromptDialogFragment newlyAddedFragment =
                    sPendingSupportUpdatePromptDialogFragments.get(fm);

            SupportUpdatePromptDialogFragment actualFragment =
                    (SupportUpdatePromptDialogFragment) fm.findFragmentByTag(FRAGMENT_TAG);
            if (actualFragment == newlyAddedFragment) {
                return true;
            }

            if (actualFragment != null) {
                throw new IllegalStateException(
                        "We've added two fragments!"
                                + " Old: " + actualFragment + " New: " + newlyAddedFragment);
            }

            // If our parent was destroyed, we're never going to be able to add our fragment, so we
            // should just clean it up and abort.
            // Similarly if we've already tried to add the fragment, waited a frame, then tried to
            // add the fragment a second time and still the fragment isn't present, we're unlikely
            // to be able to do so if we retry a third time. This is easy to reproduce in Robolectric
            // by obtaining an Activity but not creating it. If we continue to loop forever,
            // we break tests and, if it happens in the real world, might leak memory and waste
            // a bunch of CPU/battery.
            if (hasAttemptedToAddFragmentTwice || fm.isDestroyed()) {
                if (fm.isDestroyed()) {
                    Log.w(TAG, "Parent was destroyed before our Fragment could be added");
                } else {
                    Log.e(TAG, "Tried adding Fragment twice and failed twice, giving up!");
                }
                return true;
            }

            // Otherwise we should make another attempt to commit the fragment and loop back again
            // in the next frame to verify.
            fm.beginTransaction().add(newlyAddedFragment, FRAGMENT_TAG).commitNowAllowingStateLoss();
            MergeAppUpdateChecker.getSingleton(newlyAddedFragment.getThemedContext()).mH
                    .obtainMessage(
                            H.MSG_REMOVE_SUPPORT_FRAGMENT_MANAGERS_PENDING_ADD,
                            HAS_ATTEMPTED_TO_ADD_FRAGMENT_TWICE, /* arg2= */ 0, fm)
                    .sendToTarget();
            Log.d(TAG, "We failed to add our Fragment the first time around, trying again...");
            return false;
        }
    }

    @VisibleForTesting
    public static final class UpdateAppService extends Service {

        @SuppressLint("StaticFieldLeak")
        @Synthetic Context mContext;
        private String mPkgName;

        @Synthetic NotificationManager mNotificationManager;
        @Synthetic NotificationCompat.Builder mNotificationBuilder;
        private static final int ID_NOTIFICATION = 20200330;

        private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
        // We want at least 2 threads and at most 4 threads to download the new apk,
        // preferring to have 1 less than the CPU count to avoid saturating the CPU
        // with background work
        private static final int COUNT_DOWNLOAD_APP_TASK = Math.max(2, Math.min(CPU_COUNT - 1, 4));

        @Synthetic final List<DownloadAppPartTask> mDownloadAppPartTasks = new LinkedList<>();
        private List<String> mPendingAppPartLinks;
        @Synthetic String[] mAppPartLinks;
        @Synthetic File[] mApkParts;
        @Synthetic File mApk;
        @Synthetic int mApkLength;
        @Synthetic final AtomicInteger mProgress = new AtomicInteger();

        private CancelAppUpdateReceiver mReceiver;

        @Synthetic final AtomicBoolean mCanceled = new AtomicBoolean();
        @Synthetic volatile boolean mRunning;

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            mProgress.set(0);
            mCanceled.set(false);
            mRunning = true;

            mContext = getApplicationContext();
            mPkgName = getPackageName();

            mNotificationManager = (NotificationManager)
                    mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = NotificationChannelManager.getDownloadNotificationChannelId(mContext);
            RemoteViews nv = createNotificationView();
            mNotificationBuilder = new NotificationCompat.Builder(mContext, channelId)
                    .setSmallIcon(R.drawable.ic_media_app_notification)
                    .setTicker(mContext.getString(R.string.downloadingUpdates))
                    .setCustomContentView(nv)
                    .setCustomBigContentView(nv)
                    .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                    .setColor(Consts.COLOR_ACCENT)
                    .setDefaults(0)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET);

            startForeground(ID_NOTIFICATION, mNotificationBuilder.build());

            mReceiver = new CancelAppUpdateReceiver();
            registerReceiver(mReceiver, new IntentFilter(CancelAppUpdateReceiver.ACTION));

            Executors.THREAD_POOL_EXECUTOR.execute(() -> {
                //noinspection ConstantConditions
                mApk = new File(Files.getAppExternalFilesDir(),
                        intent.getStringExtra(EXTRA_APP_NAME) + " "
                                + intent.getStringExtra(EXTRA_VERSION_NAME).replace(".", "_")
                                + ".apk");
                mApkLength = intent.getIntExtra(EXTRA_APP_LENGTH, 0);
                if (mApk.exists()) {
                    final String sha1 = intent.getStringExtra(EXTRA_APP_SHA1);
                    // 如果应用已经下载过了，则直接弹出安装提示通知
                    if (mApk.length() == mApkLength
                            && ObjectsCompat.equals(FileUtils.getFileSha1(mApk), sha1)) {
                        if (!mCanceled.get()) {
                            if (Configs.DEBUG_APP_UPDATE) {
                                Log.d(TAG, "Stop service when request apk is already downloaded.");
                            }
                            stopServiceAndShowInstallAppPrompt();
                        }
                        return;
                        // 否则先删除旧的apk
                    } else {
                        //noinspection ResultOfMethodCallIgnored
                        mApk.delete();
                    }
                }
                mAppPartLinks = intent.getStringArrayExtra(EXTRA_APP_PART_LINKS);
                //noinspection ConstantConditions
                final int length = mAppPartLinks.length;
                mApkParts = new File[length];
                mPendingAppPartLinks = new LinkedList<>();
                for (int i = 0; i < length; i++) {
                    mPendingAppPartLinks.add(mAppPartLinks[i]);
                    mApkParts[i] = new File(mApk.getPath().replace(".apk", i + ".apk"));
                }
                getHandler().post(() -> {
                    for (int i = 0; i < COUNT_DOWNLOAD_APP_TASK; i++) {
                        enqueueNextTask();
                    }
                });
            });
            return START_REDELIVER_INTENT;
        }

        @Synthetic void enqueueNextTask() {
            if (mDownloadAppPartTasks.size() < COUNT_DOWNLOAD_APP_TASK
                    && mPendingAppPartLinks.size() > 0
                    && !mCanceled.get()) {
                int index = ArraysKt.indexOf(mAppPartLinks, mPendingAppPartLinks.remove(0));
                DownloadAppPartTask task = new DownloadAppPartTask();
                mDownloadAppPartTasks.add((DownloadAppPartTask)
                        task.executeOnExecutor(Executors.THREAD_POOL_EXECUTOR, index));
            }
        }

        @SuppressLint("UnspecifiedImmutableFlag")
        @Synthetic RemoteViews createNotificationView() {
            RemoteViews nv = new RemoteViews(mPkgName, R.layout.notification_download_app);
            nv.setOnClickPendingIntent(R.id.btn_cancel_danv,
                    PendingIntent.getBroadcast(
                            mContext,
                            0,
                            new Intent(CancelAppUpdateReceiver.ACTION),
                            0));
            return nv;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (Configs.DEBUG_APP_UPDATE) {
                Log.d(TAG, "Service is destroyed.");
            }

            if (!mCanceled.getAndSet(true)) {
                if (mRunning) {
                    cancel(true);
                }
            }

            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        void cancel(boolean removeNotificationNow) {
            if (Configs.DEBUG_APP_UPDATE) {
                Log.d(TAG, "Cancel tasks that are still active.");
            }
            for (DownloadAppPartTask task : mDownloadAppPartTasks) {
                task.cancel(false);
            }

            deleteApkParts();

            stopService(removeNotificationNow);
        }

        private void deleteApkParts() {
            if (mApkParts != null) {
                Executors.THREAD_POOL_EXECUTOR.execute(() -> {
                    for (File apkPart : mApkParts) {
                        //noinspection ResultOfMethodCallIgnored
                        apkPart.delete();
                    }
                });
            }
        }

        @Synthetic void stopService(boolean removeNotificationNow) {
            mRunning = false;
            if (Configs.DEBUG_APP_UPDATE) {
                Log.d(TAG, "Waiting for any progress notification to complete to be sent. "
                        + "removeNotificationNow=" + removeNotificationNow);
            }
            // Sync always to wait for any progress notification to complete to be sent...
            //noinspection SynchronizeOnNonFinalField
            synchronized (mNotificationManager) {
                if (removeNotificationNow) {
                    stopForeground(false);
                    mNotificationManager.cancel(ID_NOTIFICATION);
                }
            }
            getHandler().sendEmptyMessage(H.MSG_STOP_UPDATE_APP_SERVICE);
        }

        @Synthetic void stopServiceAndShowInstallAppPrompt() {
            stopService(false);

            // Postpone showing a notification to remind the user to install the downloaded app,
            // because the Context's stopService method will asynchronously cancel
            // the notification with id ID_NOTIFICATION, resulting in the pending notification
            // to be canceled as the service dies.
            Handler handler = getHandler();
            handler.post(() -> {
                Runnable action = () -> {
                    if (Configs.DEBUG_APP_UPDATE) {
                        Log.d(TAG, "Show app installation prompt notification now.");
                    }
                    showInstallAppPromptNotification(mApk);
                };
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Configs.DEBUG_APP_UPDATE) {
                        Log.d(TAG, "Start checking if any notifications use id " + ID_NOTIFICATION);
                    }
                    postTillConditionMeets(handler, action,
                            () -> !hasNotification(mNotificationManager, ID_NOTIFICATION, null));
                } else {
                    if (Configs.DEBUG_APP_UPDATE) {
                        Log.d(TAG, "Postpone showing app installation prompt notification for "
                                + Configs.DELAY_SEND_NOTIFICATION_WITH_JUST_STOPPED_FOREGROUND_SERVICE_ID
                                + " milliseconds.");
                    }
                    // FIXME: the upcoming prompt notification may still be canceled by
                    //        ActivityManagerService though this is a small probability
                    //        event, since it is running on the server process and we can't
                    //        ensure the action will only be performed after it cancels
                    //        the notification with the same id.
                    handler.postDelayed(action,
                            Configs.DELAY_SEND_NOTIFICATION_WITH_JUST_STOPPED_FOREGROUND_SERVICE_ID);
                }
            });
        }

        void showInstallAppPromptNotification(File apk) {
            if (apk == null || !apk.exists() || apk.length() != mApkLength) {
                Toast.makeText(mContext, R.string.theInstallationPackageHasBeenDamaged,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Intent it = Utils.createPackageInstaller(mContext, apk);

//            mContext.startActivity(it); // MIUI默认应用在后台时无法弹出界面

            String channelId = NotificationChannelManager.getMessageNotificationChannelId(mContext);
            String title = mContext.getString(R.string.newAppDownloaded);
            @SuppressLint("UnspecifiedImmutableFlag")
            PendingIntent pi = PendingIntent.getActivity(mContext, 0, it, 0);
            mNotificationManager.notify(
                    ID_NOTIFICATION,
                    mNotificationBuilder
                            .setChannelId(channelId)
                            .setTicker(title)
                            .setContentTitle(title)
                            .setContentText(mContext.getString(R.string.clickToInstallIt))
                            .setContentIntent(pi)
//                            .setFullScreenIntent(pi, true)
                            .setAutoCancel(true)
                            .setCustomContentView(null)
                            .setCustomBigContentView(null)
                            .setStyle(null)
                            .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS)
                            .setOnlyAlertOnce(false)
                            .setPriority(NotificationCompat.PRIORITY_HIGH) // 高优先级以显示抬头式通知
                            .setCategory(NotificationCompat.CATEGORY_PROMO)
                            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                            .build());
        }

        @Synthetic Handler getHandler() {
            return MergeAppUpdateChecker.getSingleton(mContext).mH;
        }

        @Synthetic void onConnectionTimeout() {
            if (!mCanceled.getAndSet(true)) {
                getHandler().post(() -> {
                    cancel(false);
                    Toast.makeText(mContext,
                            R.string.connectionTimeout, Toast.LENGTH_SHORT).show();
                });
            }
        }

        @Synthetic void onReadTimeout() {
            if (!mCanceled.getAndSet(true)) {
                getHandler().post(() -> {
                    cancel(false);
                    Toast.makeText(mContext,
                            R.string.readTimeout, Toast.LENGTH_SHORT).show();
                });
            }
        }

        @Synthetic void onDownloadError() {
            if (!mCanceled.getAndSet(true)) {
                getHandler().post(() -> {
                    cancel(false);
                    Toast.makeText(mContext,
                            R.string.downloadError, Toast.LENGTH_SHORT).show();
                });
            }
        }

        @SuppressLint("StaticFieldLeak")
        private final class DownloadAppPartTask extends AsyncTask<Integer, Integer, Void> {
            DownloadAppPartTask() {
            }

            @Override
            protected Void doInBackground(Integer... indices) {
                final int index = indices[0];

                HttpURLConnection conn = null;
                InputStream in = null;
                OutputStream out = null;
                try {
                    URL url = new URL(mAppPartLinks[index]);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(TIMEOUT_CONNECTION);
//                    conn.setReadTimeout(TIMEOUT_READ);

                    // 从服务器请求全部资源返回 200 ok；从服务器请求部分资源返回 206 ok
                    // final int responseCode = conn.getResponseCode();

                    File apkPart = mApkParts[index];
                    if (apkPart.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        apkPart.delete();
                    }

                    in = conn.getInputStream();
                    out = new FileOutputStream(apkPart);

                    int len;
                    final byte[] buffer = new byte[8 * 1024];
                    while (!mCanceled.get() && (len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);

                        if (!mCanceled.get()) {
//                            publishProgress(len);
                            notifyProgressUpdated(mProgress.addAndGet(len));
                        }
                    }
                } catch (ConnectTimeoutException e) {
                    Log.w(TAG, e);
                    onConnectionTimeout();
                } catch (SocketTimeoutException e) {
                    Log.w(TAG, e);
                    onReadTimeout();
                } catch (IOException e) {
                    Log.w(TAG, e);
                    onDownloadError();
                } finally {
                    IOUtils.closeSilently(out);
                    IOUtils.closeSilently(in);
                    if (conn != null) {
                        conn.disconnect();
                    }
                }

                return null;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
//                notifyProgressUpdated(mProgress += values[0]);
            }

            @SuppressWarnings("SynchronizeOnNonFinalField")
            private void notifyProgressUpdated(int progress) {
                float progressPercent = (float) progress / (float) mApkLength * 100f;
                RemoteViews nv = createNotificationView();
                nv.setProgressBar(R.id.progress, mApkLength, progress, false);
                nv.setTextViewText(R.id.text_percentProgress,
                        mContext.getString(R.string.percentProgress, progressPercent));
                nv.setTextViewText(R.id.text_charsequenceProgress,
                        mContext.getString(R.string.charsequenceProgress,
                                FileUtils.formatFileSize(progress),
                                FileUtils.formatFileSize(mApkLength)));

                Notification n;
                synchronized (mNotificationBuilder) {
                    n = mNotificationBuilder
                            .setCustomContentView(nv)
                            .setCustomBigContentView(nv)
                            .build();
                }
                // Ensures no more progress notification to be sent when download is canceled...
                synchronized (mNotificationManager) {
                    if (mRunning && !mCanceled.get()) {
                        if (Configs.DEBUG_APP_UPDATE) {
                            Log.d(TAG, "Post a new progress notification into the status bar. "
                                    + "progressPercent=" + progressPercent);
                        }
                        mNotificationManager.notify(ID_NOTIFICATION, n);
                    }
                }
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                mDownloadAppPartTasks.remove(this);
                enqueueNextTask();
                if (mDownloadAppPartTasks.isEmpty() && !mCanceled.get()) {
                    Executors.THREAD_POOL_EXECUTOR.execute(() -> {
                        FileUtils.mergeFiles(mApkParts, mApk, true);
                        if (!mCanceled.get()) {
                            if (Configs.DEBUG_APP_UPDATE) {
                                Log.d(TAG, "All download tasks are completed. Stop service now...");
                            }
                            stopServiceAndShowInstallAppPrompt();
                        }
                    });
                }
            }
        }

        private static final class CancelAppUpdateReceiver extends BroadcastReceiver {
            static final String ACTION = CancelAppUpdateReceiver.class.getName();

            CancelAppUpdateReceiver() {
            }

            @Override
            public void onReceive(Context context, Intent intent) {
                if (Configs.DEBUG_APP_UPDATE) {
                    Log.d(TAG, "User cancels updating app and service is going to be stopped...");
                }
                MergeAppUpdateChecker.getSingleton(context)
                        .mH.sendEmptyMessage(H.MSG_STOP_UPDATE_APP_SERVICE);
            }
        }
    }
}
