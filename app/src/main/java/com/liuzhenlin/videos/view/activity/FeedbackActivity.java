/*
 * Created on 2018/04/11.
 * Copyright © 2018–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.bumptech.glide.util.Synthetic;
import com.google.android.material.snackbar.Snackbar;
import com.liuzhenlin.common.observer.OnOrientationChangeListener;
import com.liuzhenlin.common.observer.RotationObserver;
import com.liuzhenlin.common.utils.DensityUtils;
import com.liuzhenlin.common.utils.DisplayCutoutManager;
import com.liuzhenlin.common.utils.FileUtils;
import com.liuzhenlin.common.utils.OSHelper;
import com.liuzhenlin.common.utils.SystemBarUtils;
import com.liuzhenlin.common.utils.ThemeUtils;
import com.liuzhenlin.common.utils.UiUtils;
import com.liuzhenlin.galleryviewer.GalleryViewPager;
import com.liuzhenlin.swipeback.SwipeBackLayout;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.Consts;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.presenter.IFeedbackPresenter;
import com.liuzhenlin.videos.presenter.Presenter;
import com.liuzhenlin.videos.view.adapter.GalleryPagerAdapter;

import java.util.ArrayList;
import java.util.List;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;

/**
 * @author 刘振林
 */
public class FeedbackActivity extends BaseActivity implements IFeedbackView, View.OnClickListener {

    @Synthetic EditText mEnterProblemsOrAdviceEditor;
    @Synthetic TextView mWordCountIndicator;
    private TextView mPictureCountIndicator;
    private EditText mEnterContactWayEditor;
    @Synthetic Button mCommitButton;

    @Synthetic Dialog mConfirmSaveDataDialog;
    @Synthetic Dialog mPicturePreviewDialog;

    @Synthetic boolean mShouldSaveDataOnDestroy;

    @Synthetic IFeedbackPresenter mPresenter;

    @Nullable
    @Override
    public Activity getPreviousActivity() {
        return MainActivity.this$;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);
        setWillNotDrawWindowBackgroundInContentViewArea(true);

        boolean lightStatus = !ThemeUtils.isNightMode(this);
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SystemBarUtils.setTransparentStatus(window);
                SystemBarUtils.setLightStatus(window, lightStatus);
                // MIUI6...
            } else {
                final boolean isMIUI6Later = OSHelper.getMiuiVersion() >= 6;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        && isMIUI6Later) {
                    SystemBarUtils.setTransparentStatus(window);
                    SystemBarUtils.setLightStatusForMIUI(window, lightStatus);
                } else if (isMIUI6Later) {
                    SystemBarUtils.setTranslucentStatus(window, true);
                    SystemBarUtils.setLightStatusForMIUI(window, lightStatus);
                    // FlyMe4...
                } else {
                    final boolean isFlyMe4Later = OSHelper.isFlyme4OrLater();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            && isFlyMe4Later) {
                        SystemBarUtils.setTransparentStatus(window);
                        SystemBarUtils.setLightStatusForFlyme(window, lightStatus);
                    } else if (isFlyMe4Later) {
                        SystemBarUtils.setTranslucentStatus(window, true);
                        SystemBarUtils.setLightStatusForFlyme(window, lightStatus);
                        // Other Systems
                    } else {
                        SystemBarUtils.setTranslucentStatus(window, true);
                    }
                }
            }
        }

        getSwipeBackLayout().addSwipeListener(new SwipeBackLayout.SwipeListener() {
            int oldState = SwipeBackLayout.STATE_IDLE;

            @Override
            public void onScrollStateChange(int edge, int state) {
                if (oldState == SwipeBackLayout.STATE_IDLE && state != SwipeBackLayout.STATE_IDLE) {
                    UiUtils.hideSoftInput(window, true);
                }
                oldState = state;
            }

            @Override
            public void onScrollPercentChange(int edge, float percent) {
                mShouldSaveDataOnDestroy = percent == 1f;
            }
        });

        TextView backButton = findViewById(R.id.btn_back);
        backButton.setOnClickListener(this);

        TextView saveButton = findViewById(R.id.btn_saveFeedback);
        saveButton.setOnClickListener(this);

        mEnterProblemsOrAdviceEditor = findViewById(R.id.editor_enterProblemsOrAdvice);
        mWordCountIndicator = findViewById(R.id.text_wordCountIndicator);
        mPictureCountIndicator = findViewById(R.id.text_pictureCountIndicator);
        mEnterContactWayEditor = findViewById(R.id.editor_enterContactWay);
        mCommitButton = findViewById(R.id.btn_commit);

        mEnterProblemsOrAdviceEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String text = mEnterProblemsOrAdviceEditor.getText().toString();
                mWordCountIndicator.setText(getString(R.string.wordCount, text.length()));
                mCommitButton.setEnabled(text.trim().length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        mEnterProblemsOrAdviceEditor.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    EditText editText = (EditText) v;
                    // 当触摸的是EditText且当前EditText可上下滚动时，不允许父布局ScrollView拦截事件
                    if (editText.getLineCount() > TextViewCompat.getMaxLines(editText)) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });

        mPresenter = new Presenter.Provider(this).get(IFeedbackPresenter.getImplClass());
        mPresenter.attachToView(this);

        BaseAdapter adapter = mPresenter.getPictureGridAdapter();
        GridView gridView = findViewById(R.id.grid_pictures);
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener((AdapterView.OnItemClickListener) adapter);

        mCommitButton.setOnClickListener(this);

        mPresenter.onViewCreated(this, savedInstanceState);
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                mPresenter.onViewStart((IFeedbackView) owner);
            }

            @Override
            public void onResume(@NonNull LifecycleOwner owner) {
                mPresenter.onViewResume((IFeedbackView) owner);
            }

            @Override
            public void onPause(@NonNull LifecycleOwner owner) {
                mPresenter.onViewPaused((IFeedbackView) owner);
            }

            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                mPresenter.onViewStopped((IFeedbackView) owner);
            }
        });
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mPresenter.restoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mPresenter.saveInstanceState(outState);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        final FeedbackActivity _this = this;
        mPresenter.onBackPressed(new IFeedbackPresenter.OnBackPressedCallback() {
            @Override
            public void showConfirmSaveDataDialog() {
                View view = View.inflate(_this, R.layout.dialog_confirm_save, null);
                view.<TextView>findViewById(R.id.text_message)
                        .setMovementMethod(ScrollingMovementMethod.getInstance());
                view.findViewById(R.id.btn_notSave).setOnClickListener(_this);
                view.findViewById(R.id.btn_save).setOnClickListener(_this);

                mConfirmSaveDataDialog =
                        new AppCompatDialog(_this, R.style.DialogStyle_MinWidth_NoTitle);
                mConfirmSaveDataDialog.setContentView(view);
                mConfirmSaveDataDialog.setCancelable(false);
                mConfirmSaveDataDialog.setCanceledOnTouchOutside(false);
                mConfirmSaveDataDialog.show();
            }

            @Override
            public void back() {
                FeedbackActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPicturePreviewDialog != null) {
            // 当Activity被系统杀掉时，Dialog的onDismiss()不会被调用。
            // 在此手动调用以确保屏幕方向监听和观察刘海屏开关打开/关闭的ContentObserver能被暂停
            mPicturePreviewDialog.dismiss();
        }
        // 滑动返回时默认保存数据
        if (mShouldSaveDataOnDestroy) {
            mPresenter.persistentlySaveUserFilledData(true);
        }
        mPresenter.onViewDestroyed(this);
        mPresenter.detachFromView(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null && requestCode == Consts.REQUEST_CODE_GET_PICTURE) {
            Uri uri = data.getData();
            if (uri != null)
                mPresenter.addPicture(FileUtils.UriResolver.getPath(this, uri));
        }
    }

    @Override
    public void pickPicture() {
        startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"),
                Consts.REQUEST_CODE_GET_PICTURE);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_back:
                onBackPressed();
                break;
            case R.id.btn_saveFeedback:
                mPresenter.persistentlySaveUserFilledData(true);
                scrollToFinish();
                break;

            case R.id.btn_commit:
                mPresenter.sendFeedback();
                break;

            case R.id.btn_save:
                mPresenter.persistentlySaveUserFilledData(true);
            case R.id.btn_notSave:
                mConfirmSaveDataDialog.cancel();
                scrollToFinish();
                break;
        }
    }

    @Override
    public void toastResultOnUserFilledDataSaved() {
        Activity preActivity = getPreviousActivity();
        if (preActivity == null) {
            showToast(this, R.string.saveSuccessful, Toast.LENGTH_SHORT);
        } else {
            UiUtils.showUserCancelableSnackbar(preActivity.getWindow().getDecorView(),
                    R.string.saveSuccessful, Snackbar.LENGTH_SHORT);
        }
    }

    @NonNull
    @Override
    public String getFeedbackText() {
        return mEnterProblemsOrAdviceEditor.getText().toString();
    }

    @Override
    public void setFeedbackText(@NonNull String text) {
        mEnterProblemsOrAdviceEditor.setText(text);
        mEnterProblemsOrAdviceEditor.setSelection(text.length());
    }

    @NonNull
    @Override
    public String getContactWay() {
        return mEnterContactWayEditor.getText().toString().trim();
    }

    @Override
    public void setContactWayText(@NonNull String contactWay) {
        mEnterContactWayEditor.setText(contactWay);
    }

    @NonNull
    @Override
    public IFeedbackView.PictureGridViewHolder getPictureGridViewHolder(
            int position, @Nullable View convertView, @NonNull ViewGroup parent,
            @NonNull Bitmap picture, int pictureCount) {
        PictureGridViewHolder vh;
        if (convertView == null) {
            vh = new PictureGridViewHolder(parent);
            convertView = vh.itemView;
            convertView.setTag(vh);

            final int screenWidth = App.getInstance(this).getRealScreenWidthIgnoreOrientation();
            final int contentPaddingHorizontal =
                    2 * getResources().getDimensionPixelSize(R.dimen.contentPreferredPaddingHorizontal);
            final int imageMargin = DensityUtils.dp2px(this, 8);

            ViewGroup.LayoutParams lp = convertView.getLayoutParams();
            lp.height = lp.width = com.liuzhenlin.common.utils.
                    Utils.roundFloat((screenWidth - contentPaddingHorizontal - imageMargin * 2) / 3f);

            ((GridView) parent).setColumnWidth(lp.width);

            ViewGroup.LayoutParams plp = parent.getLayoutParams();
            plp.width = screenWidth - contentPaddingHorizontal;
            plp.height = lp.height;
        } else {
            vh = (PictureGridViewHolder) convertView.getTag();
        }
        vh.pictureImage.setImageBitmap(picture);
        vh.pictureText.setVisibility(position != pictureCount - 1 ? View.GONE : View.VISIBLE);
        mPictureCountIndicator.setText(getString(R.string.pictureCount, pictureCount - 1));
        return vh;
    }

    private static final class PictureGridViewHolder extends IFeedbackView.PictureGridViewHolder {
        final ImageView pictureImage;
        final TextView pictureText;

        PictureGridViewHolder(ViewGroup adapterView) {
            super(LayoutInflater.from(adapterView.getContext())
                    .inflate(R.layout.item_picture_grid, adapterView, false));
            pictureImage = itemView.findViewById(R.id.image_picture);
            pictureText = itemView.findViewById(R.id.text_picture);
        }
    }

    @Override
    public void showPicturePreviewDialog(@NonNull List<Bitmap> pictures, int currentItem) {
        if (mPicturePreviewDialog == null) {
            mPicturePreviewDialog = new PicturePreviewDialog(pictures, currentItem);
            mPicturePreviewDialog.show();
        }
    }

    @Override
    public void hidePicturePreviewDialog() {
        if (mPicturePreviewDialog != null) {
            mPicturePreviewDialog.dismiss();
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        if (mPicturePreviewDialog != null) {
            ((PicturePreviewDialog) mPicturePreviewDialog)
                    .onMultiWindowModeChanged(isInMultiWindowMode);
        }
    }

    private final class PicturePreviewDialog extends Dialog implements
            DialogInterface.OnDismissListener, View.OnClickListener, View.OnLongClickListener,
            DisplayCutoutManager.OnNotchSwitchListener {
        final Context mContext = FeedbackActivity.this;
        final Window mParentWindow;
        final Window mWindow;

        final GalleryViewPager mGalleryViewPager;
        final GalleryPagerAdapter<ImageView> mGalleryPagerAdapter;
        final FrameLayout mDeleteFrame;

        Dialog mConfirmDeletePictureDialog;

        RotationObserver mRotationObserver;
        OnOrientationChangeListener mOnOrientationChangeListener;
        boolean mIsRotationEnabled;
        int mScreenOrientation = SCREEN_ORIENTATION_PORTRAIT;

        DisplayCutoutManager mDisplayCutoutManager;

        boolean mIsInMultiWindowMode;

        PicturePreviewDialog(List<Bitmap> pictures, int currentItem) {
            super(FeedbackActivity.this, R.style.DialogStyle_PicturePreview);
            setOnDismissListener(this);

            mParentWindow = FeedbackActivity.this.getWindow();
            mWindow = getWindow();

            //noinspection ConstantConditions
            View view = View.inflate(
                    mContext,
                    R.layout.dialog_picture_preview,
                    mWindow.getDecorView().findViewById(Window.ID_ANDROID_CONTENT));

            int pictureCount = pictures.size();
            List<ImageView> images = new ArrayList<>(pictureCount);
            for (int i = 0; i < pictureCount; i++) {
                ImageView image = (ImageView) View.inflate(
                        mContext, R.layout.item_gallery_view_pager, null);
                image.setImageBitmap(pictures.get(i));
                image.setOnClickListener(this);
                image.setOnLongClickListener(this);
                images.add(image);
            }
            mGalleryPagerAdapter = new GalleryPagerAdapter<>(images);
            mGalleryViewPager = view.findViewById(R.id.galley_view_pager);
            mGalleryViewPager.setAdapter(mGalleryPagerAdapter);
            mGalleryViewPager.setItemCallback(mGalleryPagerAdapter);
            mGalleryViewPager.setCurrentItem(currentItem);
            mGalleryViewPager.setPageMargin(DensityUtils.dp2px(mContext, 25f));

            mDeleteFrame = view.findViewById(R.id.frame_btn_delete);
            mDeleteFrame.setOnClickListener(this);
            view.findViewById(R.id.btn_delete).setOnClickListener(this);

            mWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }

        @Override
        public void show() {
            if (isShowing()) {
                return;
            }

            mIsInMultiWindowMode = isInMultiWindowMode();
            if (!mIsInMultiWindowMode) {
                setLayoutInDisplayCutout(true);
            }
            mWindow.getDecorView().setOnSystemUiVisibilityChangeListener(
                    visibility -> {
                        // FIXME: 对于Dialog, 在API24（Android 7.0）及以下不起作用
                        SystemBarUtils.showSystemBars(mWindow, mIsInMultiWindowMode);
                    });
            super.show();

            mRotationObserver = new RotationObserver(
                    mParentWindow.getDecorView().getHandler(), mContext) {
                @Override
                public void onRotationChange(boolean selfChange, boolean enabled) {
                    mIsRotationEnabled = enabled;
                }
            };
            mOnOrientationChangeListener = new OnOrientationChangeListener(
                    mContext, mScreenOrientation) {
                @Override
                public void onOrientationChange(int orientation) {
                    if (orientation != SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                        if (!mIsRotationEnabled
                                && !(orientation != SCREEN_ORIENTATION_PORTRAIT
                                        && mScreenOrientation != SCREEN_ORIENTATION_PORTRAIT)) {
                            setOrientation(mScreenOrientation);
                            return;
                        }
                        mScreenOrientation = orientation;
                        setRequestedOrientation(orientation);
                        adjustVisibleRegion();
                    }
                }
            };
            mRotationObserver.startObserver();
            mOnOrientationChangeListener.setEnabled(true);
        }

        @SuppressLint("SourceLockedOrientationActivity")
        @Override
        public void onDismiss(DialogInterface dialog) {
            mPicturePreviewDialog = null;
            if (mDisplayCutoutManager != null) {
                mDisplayCutoutManager.dispose();
            }
            mOnOrientationChangeListener.setEnabled(false);
            mRotationObserver.stopObserver();
            if (mScreenOrientation != SCREEN_ORIENTATION_PORTRAIT) {
                setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
            }
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.image_picture:
                    if (mDeleteFrame.getVisibility() == View.VISIBLE) {
                        mDeleteFrame.setVisibility(View.GONE);
                        break;
                    }
                    cancel();
                    break;

                case R.id.frame_btn_delete:
                case R.id.btn_delete:
                    View view = View.inflate(mContext, R.layout.dialog_message, null);
                    TextView messageText = view.findViewById(R.id.text_message);
                    messageText.setText(R.string.areYouSureToDeleteThisPicture);
                    messageText.setMovementMethod(ScrollingMovementMethod.getInstance());
                    view.findViewById(R.id.btn_cancel).setOnClickListener(this);
                    view.findViewById(R.id.btn_ok).setOnClickListener(this);
                    mConfirmDeletePictureDialog = new AppCompatDialog(
                            mContext, R.style.DialogStyle_MinWidth_NoTitle);
                    mConfirmDeletePictureDialog.setContentView(view);
                    mConfirmDeletePictureDialog.show();
                    break;

                case R.id.btn_cancel:
                    mConfirmDeletePictureDialog.cancel();
                    mConfirmDeletePictureDialog = null;
                    break;
                case R.id.btn_ok:
                    mConfirmDeletePictureDialog.cancel();
                    mConfirmDeletePictureDialog = null;

                    final int currentItem = mGalleryViewPager.getCurrentItem();
                    mGalleryPagerAdapter.views.remove(currentItem);
                    mGalleryPagerAdapter.notifyDataSetChanged();
                    mPresenter.removePictureAt(currentItem);
                    break;
            }
        }

        @Override
        public boolean onLongClick(View v) {
            //noinspection SwitchStatementWithTooFewBranches
            switch (v.getId()) {
                case R.id.image_picture:
                    if (mDeleteFrame.getVisibility() != View.VISIBLE) {
                        mDeleteFrame.setVisibility(View.VISIBLE);
                        return true;
                    }
                    break;
            }
            return false;
        }

        void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
            mIsInMultiWindowMode = isInMultiWindowMode;
            setLayoutInDisplayCutout(!isInMultiWindowMode);
        }

        // 使View占用刘海区（如果有）
        void setLayoutInDisplayCutout(boolean in) {
            DisplayCutoutManager cutoutManager = getDisplayCutoutManager();
            cutoutManager.setLayoutInDisplayCutout(in);
            if (in) {
                cutoutManager.addOnNotchSwitchListener(this);
            } else {
                cutoutManager.removeOnNotchSwitchListener(this);
            }
            adjustVisibleRegion();
        }

        DisplayCutoutManager getDisplayCutoutManager() {
            if (mDisplayCutoutManager == null) {
                mDisplayCutoutManager = new DisplayCutoutManager(mParentWindow, mWindow);
            }
            return mDisplayCutoutManager;
        }

        void adjustVisibleRegion() {
            if (mDisplayCutoutManager == null || !mDisplayCutoutManager.isNotchSupport()) {
                return;
            }

            if (mIsInMultiWindowMode) {
                mGalleryViewPager.setPadding(0, 0, 0, 0);
                return;
            }

            boolean isNotchSupportOnEMUI = mDisplayCutoutManager.isNotchSupportOnEMUI();
            boolean isNotchHidden = mDisplayCutoutManager.isNotchHidden();
            int notchHeight = mDisplayCutoutManager.getNotchHeight();
            switch (mScreenOrientation) {
                case SCREEN_ORIENTATION_PORTRAIT:
                    mGalleryViewPager.setPadding(0, notchHeight, 0, 0);
                    break;
                case SCREEN_ORIENTATION_LANDSCAPE:
                    mGalleryViewPager.setPadding(
                            isNotchSupportOnEMUI && isNotchHidden ? 0 : notchHeight, 0,
                            0, 0);
                    break;
                case SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    mGalleryViewPager.setPadding(0, 0,
                            isNotchSupportOnEMUI && isNotchHidden ? 0 : notchHeight, 0);
                    break;
            }
        }

        @Override
        public void onNotchChange(boolean hidden) {
            adjustVisibleRegion();
        }
    }
}
