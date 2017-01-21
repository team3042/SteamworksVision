package org.usfirst.frc.team3042.steamworksvision;

import android.content.Context;
import android.hardware.camera2.CaptureRequest;
import android.util.AttributeSet;
import android.util.Log;

import org.opencv.android.BetterCamera2Renderer;
import org.opencv.android.BetterCameraGLSurfaceView;
import org.usfirst.frc.team3042.steamworksvision.communication.RobotConnection;
import org.usfirst.frc.team3042.steamworksvision.communication.TargetInfo;
import org.usfirst.frc.team3042.steamworksvision.communication.VisionUpdate;
import org.usfirst.frc.team3042.steamworksvision.communication.messages.TargetUpdateMessage;

import java.util.ArrayList;
import java.util.HashMap;


public class VisionGLSurfaceView extends BetterCameraGLSurfaceView implements BetterCameraGLSurfaceView.CameraTextureListener {

    static final String LOGTAG = "VTGLSurfaceView";

    static final int kHeight = 480;
    static final int kWidth = 640;
    static final double kCenterCol = ((double) kWidth) / 2.0 - .5;
    static final double kCenterRow = ((double) kHeight) / 2.0 - .5;

    static final int lowerH = 0;
    static final int upperH = 95;
    static final int lowerS = 62;
    static final int upperS = 255;
    static final int lowerV = 57;
    static final int upperV = 255;

    protected int frameCounter;
    protected long lastNanoTime;
    private RobotConnection robotConnection;

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

        frameCounter = 0;
        lastNanoTime = System.nanoTime();
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public boolean onCameraTexture(int texIn, int texOut, int width, int height, long image_timestamp) {
        // FPS calculating
        frameCounter++;
        if (frameCounter >= 30) {
            final int fps = (int) (frameCounter * 1e9 / (System.nanoTime() - lastNanoTime));
            Log.i(LOGTAG, "drawFrame() FPS: " + fps);
            frameCounter = 0;
            lastNanoTime = System.nanoTime();
        }

        VisionUpdate visionUpdate = new VisionUpdate(image_timestamp);

        ArrayList<TargetInfo> targets = OpenCVUtils.processImage(texIn, texOut, width, height, lowerH, upperH, lowerS, upperS, lowerV, upperV);

        for(int i = 0; i < targets.size(); i++) {
            visionUpdate.addCameraTargetInfo(targets.get(i));
        }

        if (robotConnection != null) {
            TargetUpdateMessage update = new TargetUpdateMessage(visionUpdate, System.nanoTime());
            robotConnection.send(update);
        }

        return true;
    }

    public void setRobotConnection(RobotConnection robotConnection) {
        this.robotConnection = robotConnection;
    }
}
