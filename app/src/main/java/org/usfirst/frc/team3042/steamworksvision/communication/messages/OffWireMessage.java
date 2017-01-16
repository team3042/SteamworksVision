package org.usfirst.frc.team3042.steamworksvision.communication.messages;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Message used to handle type of message and send rest of data to be parsed
 */

public class OffWireMessage extends VisionMessage {
    private String type;
    private String message = "{}";
    private boolean valid = false;

    public OffWireMessage(String message) {
        try {
            JSONObject reader = new JSONObject(message);
            type = reader.getString("type");
            message = reader.getString("message");
            valid = true;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public String getType() {
        return type == null ? "unknown" : type;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
