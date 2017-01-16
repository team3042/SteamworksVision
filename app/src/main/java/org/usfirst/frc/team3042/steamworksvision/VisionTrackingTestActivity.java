package org.usfirst.frc.team3042.steamworksvision;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.usfirst.frc.team3042.steamworksvision.communication.TargetInfo;
import org.usfirst.frc.team3042.steamworksvision.communication.VisionUpdate;
import org.usfirst.frc.team3042.steamworksvision.communication.messages.TargetUpdateMessage;

public class VisionTrackingTestActivity extends AppCompatActivity {

    TextView isConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision_tracking);

        isConnected = (TextView)findViewById(R.id.isConnected);

        final EditText xPos = (EditText)findViewById(R.id.xPos);
        final EditText yPos = (EditText)findViewById(R.id.yPos);
        final EditText timestamp = (EditText)findViewById(R.id.timestamp);

        // Setting up button to send data from fields as a test message
        final Button messageButton = (Button) findViewById(R.id.sendMessage);
        messageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int x = Integer.parseInt(xPos.getText().toString());
                int y = Integer.parseInt(yPos.getText().toString());
                int time = Integer.parseInt(timestamp.getText().toString());

                TargetInfo testTarget = new TargetInfo(x, y);
                VisionUpdate testUpdate = new VisionUpdate(time);
                testUpdate.addCameraTargetInfo(testTarget);

                TargetUpdateMessage testMessage = new TargetUpdateMessage(testUpdate, time);
                AppContext.getRobotConnection().send(testMessage);
            }
        });
    }

    protected class ConnectionTracker implements Runnable {

        @Override
        public void run() {
            while(true) {
                boolean connected = AppContext.getRobotConnection().isConnected();
                if(connected) {
                    isConnected.setText("Connected");
                } else {
                    isConnected.setText("Not Connected");
                }
            }
        }
    }
}
