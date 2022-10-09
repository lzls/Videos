/*
 * Created on 2018/04/14.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.bean.MailInfo;

/**
 * @author 刘振林
 */
public class MailUtil {
    // 163
    private static final String HOST = "smtp.163.com";
    private static final String PORT = "465";
    private static final String USER_NAME = "apps-mail-sender";
    private static final String FROM_ADDR = "apps_mail_sender@163.com"; // 发送方邮箱
    private static final String FROM_PSW = "APYDOSTDPDUOEEHQ";// 发送方邮箱授权码

    private static final String TO_ADDR = "2233788867@qq.com"; // 接收方邮箱

    private MailUtil() {
    }

    public static void sendMail(
            @NonNull Context context, @NonNull String title, @NonNull String text,
            @Nullable String textRelatedImagePath, @Nullable String... attachmentPaths) {
        sendMail(false, context, title, text, textRelatedImagePath, attachmentPaths);
    }

    public static void sendMailQuietly(
            @NonNull Context context, @NonNull String title, @NonNull String text,
            @Nullable String textRelatedImagePath, @Nullable String... attachmentPaths) {
        sendMail(true, context, title, text, textRelatedImagePath, attachmentPaths);
    }

    public static boolean sendMailSync(
            @NonNull Context context, @NonNull String title, @NonNull String text,
            @Nullable String textRelatedImagePath, @Nullable String... attachmentPaths) {
        MailInfo mailInfo = createMailInfo(title, text, textRelatedImagePath, attachmentPaths);
        return new MultiMailSender(mailInfo).sendMail();
    }

    private static MailInfo createMailInfo(
            String title, String text, String textRelatedImagePath, String[] attachmentPaths) {
        return new MailInfo(HOST, PORT, true, USER_NAME, FROM_PSW,
                FROM_ADDR, TO_ADDR, title, text, textRelatedImagePath, attachmentPaths);
    }

    private static void sendMail(
            boolean quiet, Context context,
            String title, String text, String textRelatedImagePath, String... attachmentPaths) {
        MailInfo mailInfo = createMailInfo(title, text, textRelatedImagePath, attachmentPaths);
        if (quiet) {
            Executors.THREAD_POOL_EXECUTOR.execute(() -> new MultiMailSender(mailInfo).sendMail());
        } else {
            new SendMailAsyncTask(context)
                    .executeOnExecutor(Executors.THREAD_POOL_EXECUTOR, mailInfo);
        }
    }

    private static final class SendMailAsyncTask extends AsyncTask<MailInfo, Void, Boolean>
            implements Dialog.OnCancelListener {
        ProgressDialog mProgressDialog;
        @SuppressLint("StaticFieldLeak")
        final Context mContext;

        SendMailAsyncTask(Context context) {
            mProgressDialog = new ProgressDialog(context);
            mContext = context.getApplicationContext();
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(true);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setMessage(mContext.getString(R.string.sending));
            mProgressDialog.show();
            mProgressDialog.setOnCancelListener(this);
        }

        @Override
        protected Boolean doInBackground(MailInfo... mailInfos) {
            MailInfo mailInfo = mailInfos[0];
            return new MultiMailSender(mailInfo).sendMail();
        }

        @Override
        protected void onPostExecute(Boolean successful) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            Toast.makeText(mContext, successful ? R.string.sendSuccessful : R.string.sendFailed,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            mProgressDialog = null;
            Toast.makeText(mContext, R.string.hasBeenPlacedInTheBackground, Toast.LENGTH_SHORT)
                    .show();
        }
    }
}
