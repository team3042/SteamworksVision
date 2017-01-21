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
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;
import org.usfirst.frc.team3042.steamworksvision.communication.RobotConnectionStateListener;
import org.usfirst.frc.team3042.steamworksvision.communication.RobotConnectionStatusBroadcastReceiver;

public class VisionTrackingTestActivity extends AppCompatActivity implements RobotConnectionStateListener {

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    TextView isConnected;
    RobotConnectionStatusBroadcastReceiver connectionReceiver;
    private VisionGLSurfaceView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision_tracking);

        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
        }

        tryStartCamera();
        /*
        isConnected = (TextView)findViewById(R.id.isConnected);

        connectionReceiver = new RobotConnectionStatusBroadcastReceiver(AppContext.getDefaultContext(), new ConnectionTracker());

        final EditText xPos = (EditText)findViewById(R.id.xPos);
        final EditText yPos = (EditText)findViewById(R.id.yPos);
        final EditText timestamp = (EditText)findViewById(R.id.timestamp);

        // Setting up button to send data from fields as a test message
        final Button messageButton = (Button) findViewById(R.id.sendMessage);
        messageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int x = (isInteger(xPos.getText().toString()))? Integer.parseInt(xPos.getText().toString()) : 0;
                int y = (isInteger(yPos.getText().toString()))? Integer.parseInt(yPos.getText().toString()) : 0;
                int time = (isInteger(timestamp.getText().toString()))? Integer.parseInt(timestamp.getText().toString()) : 0;

                TargetInfo testTarget = new TargetInfo(x, y, 0);
                VisionUpdate testUpdate = new VisionUpdate(System.nanoTime());
                testUpdate.addCameraTargetInfo(testTarget);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                TargetUpdateMessage testMessage = new TargetUpdateMessage(testUpdate, System.nanoTime());
                AppContext.getRobotConnection().send(testMessage);
            }
        });

        this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(AppContext.getDefaultContext(), "Could not connect", Toast.LENGTH_SHORT);
            }
        });
        */
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
        //view.setPreferences(prefs);
    }

    @Override
    public void robotConnected() {
        view.setRobotConnection(AppContext.getRobotConnection());
    }

    @Override
    public void robotDisconnected() {
        view.setRobotConnection(null);
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
