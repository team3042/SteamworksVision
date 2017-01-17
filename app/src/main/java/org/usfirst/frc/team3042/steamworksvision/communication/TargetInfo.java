package org.usfirst.frc.team3042.steamworksvision.communication;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

// Used to store information about detected targets, to be processed on the roboRIO
public class TargetInfo {
    protected double x, y, distance;

    public TargetInfo(double x, double y, double distance) {
        this.x = x;
        this.y = y;
        this.distance = distance;
    }

    private double doubleize(double value) {
        double leftover = value % 1;
        if (leftover < 1e-7) {
            value += 1e-7;
        }
        return value;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getDistance() {
        return distance;
    }

    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        try {
            j.put("x", doubleize(getX()));
            j.put("y", doubleize(getY()));
        } catch (JSONException e) {
            Log.e("TargetInfo", "Could not encode Json");
        }
        return j;
    }
}
