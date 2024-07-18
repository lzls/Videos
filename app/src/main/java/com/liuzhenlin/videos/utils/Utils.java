/*
 * Created on 2020-4-21 6:33:31 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.liuzhenlin.common.utils.LanguageUtils;
import com.liuzhenlin.videos.BuildConfig;
import com.liuzhenlin.videos.Files;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.NoSuchAlgorithmException;

import static com.liuzhenlin.common.utils.Utils.areAppSignaturesMatch;
import static com.liuzhenlin.common.utils.Utils.getAppTargetSdkVersion;
import static com.liuzhenlin.common.utils.Utils.objectToString;

/**
 * @author 刘振林
 */
public class Utils {
    private Utils() {
    }

    /**
     * Convenience function for creating an {@link Intent#ACTION_INSTALL_PACKAGE} Intent used for
     * launching the system application installer to install the given <strong>apk</strong>.
     */
    @NonNull
    public static Intent createPackageInstaller(@NonNull Context context, @NonNull File apk) {
        //noinspection deprecation
        Intent it = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Android 7.0 共享文件需要通过 FileProvider 添加临时权限，否则系统会抛出 FileUriExposedException.
        if (getAppTargetSdkVersion(context) >= Build.VERSION_CODES.N
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri contentUri = FileProvider.getUriForFile(context, Files.PROVIDER_AUTHORITY, apk);
            it.setData(contentUri);
        } else {
            it.setData(Uri.fromFile(apk));
        }
        return it;
    }

    @NonNull
    public static StringBuilder collectAppAndDeviceInfo(@NonNull Context context) {
        try {
            return collectAppAndDeviceInfoUncaught(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new StringBuilder(0);
    }

    @NonNull
    public static StringBuilder collectAppAndDeviceInfoUncaught(@NonNull Context context)
            throws PackageManager.NameNotFoundException, IOException, NoSuchAlgorithmException,
            IllegalAccessException {
        context = context.getApplicationContext();
        StringBuilder sb = new StringBuilder();

        PackageInfo pkgInfo =
                context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        sb.append("packageName=").append(pkgInfo.packageName).append('\n');
        sb.append("versionName=").append(pkgInfo.versionName).append('\n');
        sb.append("versionCode=").append(pkgInfo.versionCode).append('\n');
        sb.append("buildVariant=").append(BuildConfig.BUILD_TYPE).append('\n');
        sb.append("isOfficialApp=")
                .append(areAppSignaturesMatch(context, BuildConfig.RELEASE_SIGN_MD5))
                .append('\n');
        // Appends version of the patch applied by Sophix
        sb.append("sophixPatchNo=").append(BuildConfig.SOPHIX_PATCH_NO).append('\n');

        // Appends locale information
        sb.append('\n');
        sb.append("systemLocale=").append(LanguageUtils.getSystemLocale(context)).append('\n');
        sb.append("locale=").append(context.getResources().getConfiguration().locale).append('\n');

        sb.append('\n');
        deepAppendClassConstantFields(sb, Build.class);

        return sb;
    }

    private static void deepAppendClassConstantFields(StringBuilder sb, Class<?> clazz)
            throws IllegalAccessException {
        for (Field field : clazz.getFields()) {
            if ((field.getModifiers() & (Modifier.STATIC | Modifier.FINAL))
                    == (Modifier.STATIC | Modifier.FINAL)) {
                Package pkg = clazz.getPackage();
                String classSimpleName;
                if (pkg == null) {
                    classSimpleName = clazz.getName();
                } else {
                    classSimpleName = clazz.getName().replace(pkg.getName() + ".", "");
                }
                String fieldValue = objectToString(field.get(clazz));
                sb.append(classSimpleName).append('.').append(field.getName())
                        .append('=').append(fieldValue).append('\n');
            }
        }

        Class<?>[] classes = clazz.getClasses();
        for (Class<?> cls : classes) {
            deepAppendClassConstantFields(sb, cls);
        }
    }
}
