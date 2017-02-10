package org.usfirst.frc.team3042.steamworksvision.communication;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

// Used to store information about detected targets, to be processed on the roboRIO
public class TargetInfo {
    protected double x, y, distance = 0;

    // Used exclusively for processing on phone
    protected double centerTopY = 0, centerBottomY = 0;

    public TargetInfo(double x, double y, double distance) {
        this.x = x;
        this.y = y;
        this.distance = distance;
    }

    public TargetInfo(double x, double y, double centerTopY, double centerBottomY) {
        this.x = x;
        this.y = y;
        this.centerTopY = centerTopY;
        this.centerBottomY = centerBottomY;
    }

    private double doubleize(double value) {
        double leftover = value % 1;
        if (leftover < 1e-7) {
            value += 1e-7;
        }
        return value;
    }

    public double getX() { return x; }

    public double getY() {
        return y;
    }

    public double getDistance() {
        return distance;
    }

    public double getCenterTopY() {
        return centerTopY;
    }

    public double getCenterBottomY() {
        return centerBottomY;
    }

    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        try {
            j.put("x", doubleize(getX()));
            j.put("y", doubleize(getY()));
            j.put("distance", doubleize(getDistance()));
        } catch (JSONException e) {
            Log.e("TargetInfo", "Could not encode Json");
        }
        return j;
    }
}
