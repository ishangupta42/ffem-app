/*
 * Copyright (C) Stichting Akvo (Akvo Foundation)
 *
 * This file is part of Akvo Caddisfly.
 *
 * Akvo Caddisfly is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Akvo Caddisfly is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Akvo Caddisfly. If not, see <http://www.gnu.org/licenses/>.
 */

package org.akvo.caddisfly.sensor.striptest.ui;

import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.akvo.caddisfly.R;
import org.akvo.caddisfly.common.ConstantKey;
import org.akvo.caddisfly.helper.SoundUtil;
import org.akvo.caddisfly.model.Result;
import org.akvo.caddisfly.model.TestInfo;
import org.akvo.caddisfly.sensor.striptest.camera.CameraOperationsManager;
import org.akvo.caddisfly.sensor.striptest.camera.StripTestCameraPreview;
import org.akvo.caddisfly.sensor.striptest.models.TimeDelayDetail;
import org.akvo.caddisfly.sensor.striptest.utils.MessageUtils;
import org.akvo.caddisfly.sensor.striptest.widget.FinderPatternIndicatorView;
import org.akvo.caddisfly.ui.BaseActivity;
import org.akvo.caddisfly.widget.TimerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

/**
 * Created by markwestra on 19/07/2017
 */
@SuppressWarnings("deprecation")
public class StripMeasureActivity extends BaseActivity implements
        StripMeasureListener {

    public static final boolean DEBUG = false;
    // a handler to handle the state machine of the preview, capture, decode, fullCapture cycle
    private StriptestHandler mStriptestHandler;
    private FinderPatternIndicatorView mFinderPatternIndicatorView;
    private TimerView mTimerCountdown;
    @Nullable
    private WeakReference<Camera> wrCamera;
    private Camera mCamera;
    private StripTestCameraPreview mCameraPreview;
    private FrameLayout previewLayout;
    private WeakReference<StripMeasureActivity> mActivity;
    private TestInfo testInfo;
    private StripMeasureFragment stripMeasureFragment;
    private InstructionFragment instructionsFragment;
    private List<Result> patches;
    // CameraOperationsManager wraps the camera API
    private CameraOperationsManager mCameraOpsManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_strip_measure);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mFinderPatternIndicatorView =
                findViewById(R.id.finder_indicator);

        mTimerCountdown = findViewById(R.id.countdownTimer);

        mActivity = new WeakReference<>(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        testInfo = getIntent().getParcelableExtra(ConstantKey.TEST_INFO);

        if (testInfo != null && testInfo.getUuid() != null) {
            setTitle(testInfo.getName());
            patches = testInfo.getResults();
        } else {
            finish();
        }

        if (mCameraOpsManager == null) {
            mCameraOpsManager = new CameraOperationsManager(testInfo.getName());
        }

        // create striptestHandler
        // as this handler is created on the current thread, it is part of the UI thread.
        // So we don't want to do actual work on it - just coordinate.
        // The camera and the decoder get their own thread.
        if (mStriptestHandler == null) {
            mStriptestHandler = new StriptestHandler(this, getApplicationContext(),
                    mCameraOpsManager, mFinderPatternIndicatorView, testInfo);
        }

        mCameraOpsManager.setStriptestHandler(mStriptestHandler);

        mStriptestHandler.setStatus(StriptestHandler.State.PREPARE);

        // we use a set to remove duplicates
        Set<int[]> timeDelaySet = new HashSet<>();
        double currentPatch = -1;
        for (int i = 0; i < patches.size(); i++) {
            double nextPatch = patches.get(i).getTimeDelay();
            // if item has no colors then it is a calculated result based on formula so don't add it to set
            if (patches.get(i).getColors().size() > 0 && Math.abs(nextPatch - currentPatch) > 0.001) {
                timeDelaySet.add(new int[]{patches.get(i).getTestStage(), (int) Math.round(nextPatch)});
                currentPatch = nextPatch;
            }
        }
        List<TimeDelayDetail> timeDelays = new ArrayList<>();
        for (int[] value : timeDelaySet) {
            timeDelays.add(new TimeDelayDetail(value[0], value[1]));
        }

        Collections.sort(timeDelays);
        mStriptestHandler.setTestData(timeDelays);

        // initialize camera and start camera preview
        startCameraPreview();
    }

    private void startCameraPreview() {
        previewLayout = findViewById(R.id.camera_preview);
        mCameraPreview = mCameraOpsManager.initCamera(this);
        mCamera = mCameraPreview.getCamera();
        if (mCamera == null) {
            Toast.makeText(this.getApplicationContext(), "Could not instantiate the camera",
                    Toast.LENGTH_SHORT).show();
            finish();
        } else {
            try {
                wrCamera = new WeakReference<>(mCamera);
                previewLayout.removeAllViews();
                if (mCameraPreview != null) {
                    previewLayout.addView(mCameraPreview);
                } else {
                    finish();
                }

            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }

    // started from within camera preview
    public void initPreviewFragment() {
        try {
            if (stripMeasureFragment == null) {
                stripMeasureFragment = StripMeasureFragment.newInstance(mStriptestHandler);
                mStriptestHandler.setFragment(stripMeasureFragment);
                getSupportFragmentManager().beginTransaction().replace(
                        R.id.layout_cameraPlaceholder, stripMeasureFragment
                ).commit();
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    @Override
    public void moveToInstructions(int testStage) {
        instructionsFragment = InstructionFragment.newInstance(testInfo, testStage);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.layout_instructionLayout, instructionsFragment)
                .commit();
    }

    @Override
    public void moveToStripMeasurement() {
        getSupportFragmentManager().beginTransaction()
                .remove(instructionsFragment)
                .commit();

        if (stripMeasureFragment == null) {
            stripMeasureFragment = StripMeasureFragment.newInstance(mStriptestHandler);
        }

        getSupportFragmentManager().beginTransaction().replace(
                R.id.layout_cameraPlaceholder, stripMeasureFragment).commit();
        mStriptestHandler.setStatus(StriptestHandler.State.MEASURE);
        stripMeasureFragment.clearProgress();
        stripMeasureFragment.setMeasureText();

        // hand over to state machine
        MessageUtils.sendMessage(mStriptestHandler, StriptestHandler.START_PREVIEW_MESSAGE, 0);
    }

    @Override
    public void moveToResults() {
        // move to results activity
        Intent resultIntent = new Intent(getIntent());
        resultIntent.setClass(this, ResultActivity.class);
        resultIntent.putExtra(ConstantKey.TEST_INFO, testInfo);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        startActivity(resultIntent);
        ResultActivity.setDecodeData(StriptestHandler.getDecodeData());
    }

    @Override
    public void showTimer() {
        if (mTimerCountdown.getVisibility() != View.VISIBLE)
            mTimerCountdown.setVisibility(View.VISIBLE);
    }

    @Override
    public void playSound() {
        SoundUtil.playShortResource(this, R.raw.futurebeep2);
    }

    @Override
    public void updateTimer(int value) {
        mTimerCountdown.setProgress(value, 60);
    }

    @Override
    public void onPause() {
        releaseResources();
        if (!isFinishing()) {
            finish();
        }
        super.onPause();
    }

    private void releaseResources() {
        if (mCamera != null) {
            mCameraOpsManager.stopAutofocus();
            mCameraOpsManager.stopCamera();
            mCamera.setOneShotPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }

        if (mStriptestHandler != null) {
            mStriptestHandler.quitSynchronously();
            mStriptestHandler = null;
        }

        if (mActivity != null) {
            mActivity.clear();
            mActivity = null;
        }
        if (wrCamera != null) {
            wrCamera.clear();
            wrCamera = null;
        }

        if (mCameraPreview != null && previewLayout != null) {
            previewLayout.removeView(mCameraPreview);
            mCameraPreview = null;
        }
    }

    /**
     * Store previewLayout info in global properties for later use.
     * w: actual size of the preview window
     * h: actual size of the preview window
     * previewImageWidth: size of image returned from camera
     * previewImageHeight: size of image returned from camera
     */
    public void setPreviewProperties(int w, int h, int previewImageWidth, int previewImageHeight) {
        if (mCamera != null && mCameraPreview != null) {
            StriptestHandler.mDecodeData.setPreviewWidth(w);
            StriptestHandler.mDecodeData.setPreviewHeight(h);
            StriptestHandler.mDecodeData.setDecodeWidth(previewImageWidth);
            StriptestHandler.mDecodeData.setDecodeHeight(previewImageHeight);

            mFinderPatternIndicatorView.setMeasure(w, h, previewImageWidth, previewImageHeight);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
