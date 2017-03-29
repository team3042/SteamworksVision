package org.usfirst.frc.team3042.steamworksvision;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.florescu.android.rangeseekbar.RangeSeekBar;
import org.opencv.android.OpenCVLoader;
import org.usfirst.frc.team3042.steamworksvision.communication.RobotConnection;
import org.usfirst.frc.team3042.steamworksvision.communication.RobotConnectionStateListener;
import org.usfirst.frc.team3042.steamworksvision.communication.RobotConnectionStatusBroadcastReceiver;
import org.usfirst.frc.team3042.steamworksvision.communication.messages.OffWireMessage;

public class VisionTrackingTestActivity extends AppCompatActivity implements RobotConnectionStateListener, VisionModeStateListener {

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    TextView isConnected, targetType;
    private VisionGLSurfaceView view;
    private Preferences prefs;
    private RobotConnectionStatusBroadcastReceiver connectionReceiver;
    private VisionModeStatusBroadcastReceiver visionModeReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision_tracking);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
        }

        prefs = new Preferences(this);
        connectionReceiver = new RobotConnectionStatusBroadcastReceiver(this, this);
        visionModeReceiver = new VisionModeStatusBroadcastReceiver(this, this);

        tryStartCamera();

        isConnected = (TextView)findViewById(R.id.connected_text_view);
        targetType = (TextView)findViewById(R.id.vision_mode_text_view);

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage("This app needs camera permission")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(parent.getActivity(),
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(this.getFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    private void tryStartCamera() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

        view = (VisionGLSurfaceView) findViewById(R.id.my_gl_surface_view);
        view.setCameraTextureListener(view);
        view.setPreferences(prefs);
        TextView tv = (TextView) findViewById(R.id.fps_text_view);
    }

    // Methods for checking menu boxes, !checked is future state of the box in each method
    public boolean onHSVCheckboxClicked(MenuItem item) {
        boolean checked = item.isChecked();
        item.setChecked(!checked);

        view.setOutputHSVFrame(!checked);

        return true;
    }

    public boolean onTargetLiftClicked(MenuItem item) {
        boolean checked = item.isChecked();

        if (!checked) {
            AppContext.getRobotConnection().broadcastVisionModeLift();
        } else {
            AppContext.getRobotConnection().broadcastVisionModeBoiler();
        }

        return true;
    }

    public boolean onTargetBoilerClicked(MenuItem item) {
        boolean checked = item.isChecked();

        if (!checked) {
            AppContext.getRobotConnection().broadcastVisionModeBoiler();
        } else {
            AppContext.getRobotConnection().broadcastVisionModeLift();
        }

        return true;
    }

    public boolean openBottomSheet(MenuItem item) {
        View view = getLayoutInflater().inflate(R.layout.hsv_bottom_sheet, null);
        LinearLayout container = (LinearLayout) view.findViewById(R.id.popup_window);
        container.getBackground().setAlpha(20);


        final Dialog mBottomSheetDialog = new Dialog(VisionTrackingTestActivity.this, R.style.MaterialDialogSheet);
        mBottomSheetDialog.setContentView(view);
        mBottomSheetDialog.setCancelable(true);
        mBottomSheetDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mBottomSheetDialog.getWindow().setGravity(Gravity.BOTTOM);
        mBottomSheetDialog.show();

        final RangeSeekBar hSeekBar = (RangeSeekBar) view.findViewById(R.id.hSeekBar);
        setSeekBar(hSeekBar, getHRange());
        hSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>() {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar<?> rangeSeekBar, Integer min, Integer max) {
                Log.i("H", min + " " + max);
                prefs.setThresholdHRange(min, max);
            }
        });

        final RangeSeekBar sSeekBar = (RangeSeekBar) view.findViewById(R.id.sSeekBar);
        setSeekBar(sSeekBar, getSRange());
        sSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>() {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar<?> rangeSeekBar, Integer min, Integer max) {
                Log.i("S", min + " " + max);
                prefs.setThresholdSRange(min, max);
            }
        });

        final RangeSeekBar vSeekBar = (RangeSeekBar) view.findViewById(R.id.vSeekBar);
        setSeekBar(vSeekBar, getVRange());
        vSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener<Integer>() {
            @Override
            public void onRangeSeekBarValuesChanged(RangeSeekBar<?> rangeSeekBar, Integer min, Integer max) {
                Log.i("V", min + " " + max);
                prefs.setThresholdVRange(min, max);
            }
        });

        Button restoreButton = (Button) view.findViewById(R.id.restoreDefaultsButton);
        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.restoreDefaults();
                setSeekBar(hSeekBar, getHRange());
                setSeekBar(sSeekBar, getSRange());
                setSeekBar(vSeekBar, getVRange());
            }
        });

        return true;
    }

    private static void setSeekBar(RangeSeekBar<Integer> bar, Pair<Integer, Integer> values) {
        bar.setSelectedMinValue(values.first);
        bar.setSelectedMaxValue(values.second);
    }

    public Pair<Integer, Integer> getHRange() {
        return prefs.getThresholdHRange();
    }

    public Pair<Integer, Integer> getSRange() {
        return prefs.getThresholdSRange();
    }

    public Pair<Integer, Integer> getVRange() {
        return prefs.getThresholdVRange();
    }

    @Override
    public void robotConnected() {
        isConnected.setText("Connected");

        view.setRobotConnection(AppContext.getRobotConnection());

        switch(VisionGLSurfaceView.visionMode) {
            case Boiler:
                AppContext.getRobotConnection().send(new OffWireMessage("Boiler"));
                break;
            case Lift:
                AppContext.getRobotConnection().send(new OffWireMessage("Lift"));
                break;
        }
    }

    @Override
    public void robotDisconnected() {
        isConnected.setText("Not Connected");

        view.setRobotConnection(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public void setVisionModeLift() {
        targetType.setText("Target: Lift");

        view.visionMode = VisionMode.Lift;
    }

    @Override
    public void setVisionModeBoiler() {
        targetType.setText("Target: Boiler");

        view.visionMode = VisionMode.Boiler;
    }

    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch(NumberFormatException e) {
            return false;
        } catch(NullPointerException e) {
            return false;
        }
        // only got here if we didn't return false
        return true;
    }
}
