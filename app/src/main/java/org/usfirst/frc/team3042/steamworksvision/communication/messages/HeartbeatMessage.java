package org.usfirst.frc.team3042.steamworksvision.communication.messages;

/**
 * Message used to ensure connection is maintained
 */

public class HeartbeatMessage extends VisionMessage {
    static HeartbeatMessage inst = null;

    public static HeartbeatMessage getInstance() {
        if (inst == null) {
            inst = new HeartbeatMessage();
        }
        return inst;
    }

    @Override
    public String getType() {
        return "heartbeat";
    }

    @Override
    public String getMessage() {
        return "{}";
    }
}
