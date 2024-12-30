/*
 * Created on 2020-3-14 5:35:20 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LabeledIntent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;

import com.liuzhenlin.common.Consts;
import com.liuzhenlin.common.R;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * @author 刘振林
 */
public class ShareUtils {
    private ShareUtils() {
    }

    private static final Sharer SHARER =
            Build.VERSION.SDK_INT >= 24 ? new SharerApi24Impl() : new SharerBaseImpl();

    public static void shareText(
            @NonNull Context context,
            @NonNull String text,
            @NonNull String mimeType /* MIME type of the text */) {
        SHARER.shareText(context, text, mimeType);
    }

    public static void shareFile(
            @NonNull Context context,
            @NonNull String authority,
            @NonNull File file,
            @NonNull String defMimeType /* default file MIME type */) {
        SHARER.shareFile(context, authority, file, defMimeType);
    }

    private interface Sharer {

        void shareText(Context context, String text, String mimeType);

        void shareFile(Context context, String authority, File file, String defMimeType);

        default Intent createTextSender(String text, String mimeType) {
            return new Intent(Intent.ACTION_SEND)
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .setType(mimeType);
        }

        default Intent createFileSender(
                Context context, String authority, File file, String defMimeType) {
            Intent it = new Intent(Intent.ACTION_SEND);
            Uri uri;
            if (Utils.getAppTargetSdkVersion(context) >= Build.VERSION_CODES.N
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                uri = FileProvider.getUriForFile(context, authority, file);
            } else {
                uri = Uri.fromFile(file);
            }
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.setType(FileUtils.getMimeTypeFromPath(file.getPath(), defMimeType));
            return it;
        }

        default Intent createChooser(Context context, Intent target) {
            return Intent.createChooser(target, context.getString(R.string.share));
        }

        default ComponentName[] getExcludedComponents(Context context) {
            return sExcludedComponents.get(context);
        }

        Singleton<Context, ComponentName[]> sExcludedComponents =
                new Singleton<Context, ComponentName[]>() {
                    @NonNull
                    @Override
                    protected ComponentName[] onCreate(Context... ctxes) {
                        Intent base = new Intent(Intent.ACTION_SEND).setType("*/*");
                        List<ResolveInfo> resInfo =
                                ctxes[0].getPackageManager().queryIntentActivities(base, 0);
                        List<ComponentName> componentNames = new LinkedList<>();
                        for (ResolveInfo info : resInfo) {
                            ActivityInfo activityInfo = info.activityInfo;
                            if (activityInfo.packageName.equals(Consts.APPLICATION_ID)) {
                                componentNames.add(new ComponentName(
                                        activityInfo.packageName, activityInfo.name));
                            }
                        }
                        //noinspection ToArrayCallWithZeroLengthArrayArgument
                        return componentNames.toArray(new ComponentName[componentNames.size()]);
                    }
                };
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private static final class SharerApi24Impl implements Sharer {

        SharerApi24Impl() {
        }

        @Override
        public void shareText(Context context, String text, String mimeType) {
            startChooserActivity(context, createTextSender(text, mimeType));
        }

        @Override
        public void shareFile(Context context, String authority, File file, String defMimeType) {
            startChooserActivity(context, createFileSender(context, authority, file, defMimeType));
        }

        void startChooserActivity(Context context, Intent target) {
            Intent chooser = createChooser(context, target);
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, getExcludedComponents(context));
            context.startActivity(chooser);
        }
    }

    private static final class SharerBaseImpl implements Sharer {

        SharerBaseImpl() {
        }

        @Override
        public void shareText(Context context, String text, String mimeType) {
            List<Intent> targets =
                    currentAppFilteredShareIntents(context, createTextSender(text, mimeType));
            startChooserActivity(context, targets);
        }

        @Override
        public void shareFile(Context context, String authority, File file, String defMimeType) {
            List<Intent> targets =
                    currentAppFilteredShareIntents(
                            context, createFileSender(context, authority, file, defMimeType));
            startChooserActivity(context, targets);
        }

        void startChooserActivity(Context context, List<Intent> choices) {
            if (choices == null || choices.isEmpty()) {
                Toast.makeText(context, R.string.noAppsCanPerformThisAction, Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            Intent target =
                    // Creating a chooser with an empty intent can solve the empty cells problem
                    // that occurs when the underlying platform version is greater than 22.
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? new Intent()
                                                                   : choices.remove(0);
            Intent chooser = createChooser(context, target);
            //noinspection ToArrayCallWithZeroLengthArrayArgument
            chooser.putExtra(
                    Intent.EXTRA_INITIAL_INTENTS, choices.toArray(new Parcelable[choices.size()]));
            context.startActivity(chooser);
        }

        List<Intent> currentAppFilteredShareIntents(Context context, Intent base) {
            List<Intent> targets = new LinkedList<>();
            List<ResolveInfo> resInfo = context.getPackageManager().queryIntentActivities(base, 0);
            ComponentName[] excludedComponents = getExcludedComponents(context);
            outer:
            for (ResolveInfo info : resInfo) {
                ActivityInfo activityInfo = info.activityInfo;
                // judgments : activityInfo.packageName, activityInfo.name, etc.
                for (ComponentName excludedComponent : excludedComponents) {
                    if (activityInfo.packageName.equals(excludedComponent.getPackageName())
                            && activityInfo.name.equals(excludedComponent.getClassName())) {
                        continue outer;
                    }
                }
                Intent target = new LabeledIntent(
                        base, activityInfo.packageName, activityInfo.labelRes, activityInfo.icon);
                target.setClassName(activityInfo.packageName, activityInfo.name);
                targets.add(target);
            }
            return targets;
        }
    }
}
