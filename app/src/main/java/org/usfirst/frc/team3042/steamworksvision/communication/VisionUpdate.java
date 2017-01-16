package org.usfirst.frc.team3042.steamworksvision.communication;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// Data to be sent to roboRIO, includes time data and any targets detected
public class VisionUpdate {
    protected List<TargetInfo> targets;
    protected long captured = 0;

    public VisionUpdate(long capturedAtTimestamp) {
        captured = capturedAtTimestamp;
        targets = new ArrayList<>(3);
    }

    public void addCameraTargetInfo(TargetInfo t) {
        targets.add(t);
    }

    public String getSendableJsonString(long timestamp) {
        long captured_ago = (timestamp - captured) / 1000000L;  // nanos to millis
        JSONObject j = new JSONObject();
        try {
            j.put("capturedAgoMs", captured_ago);
            JSONArray arr = new JSONArray();
            for (TargetInfo t : targets) {
                if (t != null) {
                    arr.put(t.toJson());
                }
            }
            j.put("targets", arr);
        } catch (JSONException e) {
            Log.e("VisionUpdate", "Could not encode JSON");
        }

        return j.toString();
    }
}