package org.usfirst.frc.team3042.steamworksvision;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class VisionModeStatusBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION_VISION_MODE_LIFT = "action_vision_mode_lift";
    public static final String ACTION_VISION_MODE_BOILER = "action_vision_mode_boiler";

    private VisionModeStateListener listener;

    public VisionModeStatusBroadcastReceiver(Context context, VisionModeStateListener listener) {
        this.listener = listener;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_VISION_MODE_LIFT);
        intentFilter.addAction(ACTION_VISION_MODE_BOILER);
        context.registerReceiver(this, intentFilter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_VISION_MODE_LIFT.equals(intent.getAction())) {
            //listener.setVisionModeLift();
        } else if (ACTION_VISION_MODE_BOILER.equals(intent.getAction())) {
            //listener.setVisionModeBoiler();
        }
    }
}
