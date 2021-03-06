package org.usfirst.frc.team3042.steamworksvision;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.widget.TextView;

import org.opencv.android.BetterCamera2Renderer;
import org.opencv.android.BetterCameraGLSurfaceView;
import org.usfirst.frc.team3042.steamworksvision.communication.RobotConnection;
import org.usfirst.frc.team3042.steamworksvision.communication.TargetInfo;
import org.usfirst.frc.team3042.steamworksvision.communication.VisionUpdate;
import org.usfirst.frc.team3042.steamworksvision.communication.messages.TargetUpdateMessage;

import java.util.ArrayList;
import java.util.HashMap;


public class VisionGLSurfaceView extends BetterCameraGLSurfaceView implements BetterCameraGLSurfaceView.CameraTextureListener {

    static final String LOGTAG = "VGLSurfaceView";

    static final int kHeight = 480;
    static final int kWidth = 640;
    static final double kCenterCol = ((double) kWidth) / 2.0 - .5;
    static final double kCenterRow = ((double) kHeight) / 2.0 - .5;

    protected int frameCounter;
    protected long lastNanoTime;
    TextView fpsText = null;
    private RobotConnection robotConnection;
    private Preferences prefs;
    protected boolean outputHSVFrame = false;

    //Enum to control the vision mode
    public static VisionMode visionMode = VisionMode.Lift;

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

    public void setOutputHSVFrame(boolean outputFrame) {
        outputHSVFrame = outputFrame;
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
            if (fpsText != null) {
                Runnable fpsUpdater = new Runnable() {
                    public void run() {
                        fpsText.setText("FPS: " + fps);
                    }
                };
                new Handler(Looper.getMainLooper()).post(fpsUpdater);
            } else {
                Log.d(LOGTAG, "mFpsText == null");
                fpsText = (TextView) ((Activity) getContext()).findViewById(R.id.fps_text_view);
            }
            frameCounter = 0;
            lastNanoTime = System.nanoTime();
        }

        VisionUpdate visionUpdate = new VisionUpdate(image_timestamp);

        Pair<Integer, Integer> hRange = prefs != null ? prefs.getThresholdHRange() : blankPair();
        Pair<Integer, Integer> sRange = prefs != null ? prefs.getThresholdSRange() : blankPair();
        Pair<Integer, Integer> vRange = prefs != null ? prefs.getThresholdVRange() : blankPair();

        ArrayList<TargetInfo> targets = new ArrayList<TargetInfo>();

        switch(visionMode){
            case Boiler:
                targets = OpenCVUtils.processBoilerImage(texIn, texOut, width, height, hRange.first, hRange.second,
                        sRange.first, sRange.second, vRange.first, vRange.second, outputHSVFrame);

                for(TargetInfo currentTarget : targets) {
                    double x = Math.atan((currentTarget.getX() - kCenterCol) / getFocalLengthPixels());
                    double y = Math.atan((currentTarget.getY() - kCenterRow) / getFocalLengthPixels());

                    double centerTopY = Math.atan((currentTarget.getCenterTopY() - kCenterRow) / getFocalLengthPixels());
                    double centerBottomY = Math.atan((currentTarget.getCenterBottomY() - kCenterRow) / getFocalLengthPixels());

                    // Distance calculation in inches determined by fitting curve to experimental data
                    double distance = Math.abs(centerBottomY - centerTopY); // TODO: Calibrate

                    visionUpdate.addCameraTargetInfo(new TargetInfo(x, y, distance));
                    Log.i(LOGTAG, "Target at: (" + x + ", " + y + ") with distance: " + distance);
                }
                break;
            case Lift:
                targets = OpenCVUtils.processLiftImage(texIn, texOut, width, height, hRange.first, hRange.second,
                        sRange.first, sRange.second, vRange.first, vRange.second, outputHSVFrame);

                for(TargetInfo currentTarget : targets) {
                    double x = Math.atan((currentTarget.getX() - kCenterCol) / getFocalLengthPixels());
                    double y = Math.atan((currentTarget.getY() - kCenterRow) / getFocalLengthPixels());

                    double centerTopY = Math.atan((currentTarget.getCenterTopY() - kCenterRow) / getFocalLengthPixels());
                    double centerBottomY = Math.atan((currentTarget.getCenterBottomY() - kCenterRow) / getFocalLengthPixels());

                    // Distance calculation in inches determined by fitting curve to experimental data
                    double distance = 12 * (0.432 * Math.pow(Math.abs(centerBottomY - centerTopY), -0.95));

                    visionUpdate.addCameraTargetInfo(new TargetInfo(x, y, distance));
                    Log.i(LOGTAG, "Target at: (" + x + ", " + y + ") with distance: " + distance);
                }
                break;
        }

        if (robotConnection != null && targets.size() != 0) {
            TargetUpdateMessage update = new TargetUpdateMessage(visionUpdate, System.nanoTime());
            robotConnection.send(update);
        }

        return true;
    }

    public void setRobotConnection(RobotConnection robotConnection) {
        this.robotConnection = robotConnection;
    }

    public void setPreferences(Preferences prefs) {
        this.prefs = prefs;
    }

    private static Pair<Integer, Integer> blankPair() {
        return new Pair<Integer, Integer>(0, 255);
    }
}

