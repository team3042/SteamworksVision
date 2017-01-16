package org.usfirst.frc.team3042.steamworksvision.communication.messages;

import org.usfirst.frc.team3042.steamworksvision.communication.VisionUpdate;

public class TargetUpdateMessage extends VisionMessage {
    VisionUpdate update;
    long timestamp;

    public TargetUpdateMessage(VisionUpdate update, long timestamp) {
        this.update = update;
        this.timestamp = timestamp;
    }

    @Override
    public String getType() {
        return "targets";
    }

    @Override
    public String getMessage() {
        return update.getSendableJsonString(timestamp);
    }
}
