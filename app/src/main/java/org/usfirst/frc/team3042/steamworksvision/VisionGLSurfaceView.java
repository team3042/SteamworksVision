package org.usfirst.frc.team3042.steamworksvision;

import android.content.Context;
import android.hardware.camera2.CaptureRequest;
import android.util.AttributeSet;

import org.opencv.android.BetterCamera2Renderer;
import org.opencv.android.BetterCameraGLSurfaceView;

import java.util.HashMap;


public class VisionGLSurfaceView extends BetterCameraGLSurfaceView implements BetterCameraGLSurfaceView.CameraTextureListener {

    static final int kHeight = 480;
    static final int kWidth = 640;
    static final double kCenterCol = ((double) kWidth) / 2.0 - .5;
    static final double kCenterRow = ((double) kHeight) / 2.0 - .5;

    static BetterCamera2Renderer.Settings getCameraSettings() {
        BetterCamera2Renderer.Settings settings = new BetterCamera2Renderer.Settings();
        settings.height = kHeight;
        settings.width = kWidth;
        settings.camera_settings = new HashMap<>();
        settings.camera_settings.put(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        settings.camera_settings.put(CaptureRequest.SENSOR_EXPOSURE_TIME, 1000000L);
        settings.camera_settings.put(CaptureRequest.LENS_FOCUS_DISTANCE, .2f);
        return settings;
    }

    public VisionGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs, getCameraSettings());
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public boolean onCameraTexture(int texIn, int texOut, int width, int height, long image_timestamp) {

        return true;
    }
}
