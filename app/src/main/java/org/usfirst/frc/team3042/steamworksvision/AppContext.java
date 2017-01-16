package org.usfirst.frc.team3042.steamworksvision;

import android.app.Application;
import android.content.Context;

import org.usfirst.frc.team3042.steamworksvision.communication.RobotConnection;

public class AppContext extends Application {
    private AppContext instance;

    private static AppContext app;

    private RobotConnection rc;

    public AppContext() {
        super();
        app = this;
    }

    public static Context getDefaultContext() {
        return app.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        rc = new RobotConnection(getDefaultContext());
        rc.start();
    }

    public static RobotConnection getRobotConnection() {
        return app.rc;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        rc.stop();
    }
}
