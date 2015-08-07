package org.akvo.akvoqr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;

import org.akvo.akvoqr.calibration.CalibrationCard;
import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by linda on 7/7/15.
 */
public class CameraActivity extends BaseCameraActivity implements CameraViewListener{

    private Camera mCamera;
    private FrameLayout preview;
    private BaseCameraView mPreview;
    MyPreviewCallback previewCallback;
    private String TAG = "CameraActivity";
    private Intent intent;
    private boolean testing = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

       intent = new Intent(this, ResultActivity.class);
    }

    private void init()
    {
        // Create an instance of Camera
        mCamera = TheCamera.getCameraInstance();

        previewCallback = MyPreviewCallback.getInstance(this);

        if(mCamera!=null) {
            // Create our Preview view and set it as the content of our activity.
            mPreview = new BaseCameraView(this, mCamera);
            preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(mPreview);

        }
    }
    public void onPause()
    {
        if(mCamera!=null) {

            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }

        if(mPreview!=null)
        {
            preview.removeView(mPreview);
            mPreview = null;
        }
        Log.d(TAG, "onPause OUT mCamera, mCameraPreview: " + mCamera + ", " + mPreview);

        MyPreviewCallback.firstTime = true;
        super.onPause();

    }

    public void onStop()
    {
        Log.d(TAG, "onStop OUT mCamera, mCameraPreview: " + mCamera + ", " + mPreview);

        super.onStop();
    }

    public void onResume()
    {
        super.onResume();
        if(mCamera!=null)
        {
            try {
                mCamera.reconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else
        {
            init();
        }
        Log.d(TAG, "onResume OUT mCamera, mCameraPreview: " + mCamera + ", " + mPreview);

    }
    public void onStart()
    {
        super.onStart();

        Log.d(TAG, "onStart OUT mCamera, mCameraPreview: " + mCamera + ", " + mPreview);

    }

    boolean focused;
    @Override
    public void getMessage(int what) {
        if(mCamera!=null && !isFinishing()) {
            if (what == 0) {


//                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
//                        @Override
//                        public void onAutoFocus(boolean success, Camera camera) {
//                            if (success) focused = true;
//
//                        }
//                    });
                mCamera.setOneShotPreviewCallback(previewCallback);

            } else {

                mCamera.setOneShotPreviewCallback(null);
            }
        }
    }

    @Override
    public void sendData(byte[] data, int format, int width, int height) {

        try {
            System.out.println("***image format in CameraActivity: " + format +
                    " data: " + data.length + "size: " + width + ", " + height);


            intent.putExtra("data", data);
            intent.putExtra("format", format);
            intent.putExtra("width", width);
            intent.putExtra("height", height);
           // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private final int MAX_ITER = 100;
    private int iter=0;
    @Override
    public void setBitmap(Bitmap bitmap) {


        if(testing)
        {
            if(iter<MAX_ITER && !isFinishing()) {

                iter++;
                if (ResultActivity.stripColors.size() == 2) {
                    ResultActivity.numSuccess++;
                }

                mCamera.setOneShotPreviewCallback(previewCallback);
                System.out.println("***TEST RESULTS: " + ResultActivity.numSuccess + "out of " + iter);
            }
            else {
                ResultActivity.numSuccess = 0;
                System.out.println("***FINAL TEST RESULTS: " + ResultActivity.numSuccess + "out of " + MAX_ITER);
            }
        }
        else
        {
            double ratio = (double) bitmap.getHeight() / (double) bitmap.getWidth();
            int width = 800;
            int height = (int) Math.round(ratio * width);
            System.out.println("***bitmap width: " + bitmap.getWidth() + " height: " + bitmap.getHeight());
            System.out.println("***bitmap calc width: " + width + " height: " + height + " ratio: " + ratio);
            try {
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();

            sendData(bitmapdata, ImageFormat.RGB_565, bitmap.getWidth(), bitmap.getHeight());

            bitmap.recycle();

            finish();
        }
    }

    @Override
    public Mat calibrateImage(Mat mat)
    {
        CalibrationCard calibrationCard = new CalibrationCard();
        return calibrationCard.calibrateImage(this, mat);

    }
}
