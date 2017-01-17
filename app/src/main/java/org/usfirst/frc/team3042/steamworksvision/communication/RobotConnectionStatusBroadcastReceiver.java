package org.usfirst.frc.team3042.steamworksvision.communication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

// Recieves updates to send to listener whenever robot connection changes state
public class RobotConnectionStatusBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION_ROBOT_CONNECTED = "action_robot_connected";
    public static final String ACTION_ROBOT_DISCONNECTED = "action_robot_disconnected";

    private RobotConnectionStateListener listener;

    public RobotConnectionStatusBroadcastReceiver(Context context, RobotConnectionStateListener listener) {
        this.listener = listener;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_ROBOT_CONNECTED);
        intentFilter.addAction(ACTION_ROBOT_DISCONNECTED);
        context.registerReceiver(this, intentFilter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_ROBOT_CONNECTED.equals(intent.getAction())) {
            listener.robotConnected();
        } else if (ACTION_ROBOT_DISCONNECTED.equals(intent.getAction())) {
            listener.robotDisconnected();
        }
    }
}
