package org.usfirst.frc.team3042.steamworksvision.communication;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.usfirst.frc.team3042.steamworksvision.VisionGLSurfaceView;
import org.usfirst.frc.team3042.steamworksvision.VisionMode;
import org.usfirst.frc.team3042.steamworksvision.communication.messages.HeartbeatMessage;
import org.usfirst.frc.team3042.steamworksvision.communication.messages.OffWireMessage;
import org.usfirst.frc.team3042.steamworksvision.communication.messages.VisionMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class RobotConnection {

    public static final int ROBOT_PORT = 3042;
    public static final String ROBOT_PROXY_HOST = "127.0.0.1";
    public static final int CONNECTOR_SLEEP_MS = 100;
    public static final int THRESHOLD_HEARTBEAT = 800;
    public static final int SEND_HEARTBEAT_PERIOD = 100;

    private int port;
    private String host;
    private Context context;
    private boolean running = true;
    private boolean connected = false;
    volatile private Socket socket;
    private Thread connectThread, readThread, writeThread;

    private long lastHeartbeatSent = System.currentTimeMillis();
    private long lastHeartbeatReceived = 0;

    private ArrayBlockingQueue<VisionMessage> toSend = new ArrayBlockingQueue<VisionMessage>(30);

    // Thread used to send messages from queue to roboRIO whenever they exist
    protected class WriteThread implements Runnable {

        @Override
        public void run() {
            while (running) {
                VisionMessage nextToSend = null;
                try {
                    nextToSend = toSend.poll(250, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.e("WriteThead", "Couldn't poll queue");
                }
                if (nextToSend == null) {
                    continue;
                }
                sendToWire(nextToSend);
            }
        }
    }

    // Thread monitoring input stream from roboRIO, handling messages as they are received
    protected class ReadThread implements Runnable {

        public void handleMessage(VisionMessage message) {
            if ("heartbeat".equals(message.getType())) {
                lastHeartbeatReceived = System.currentTimeMillis();
            } else if(message.getType().equals("targetType")){
                if (message.getMessage() == "boiler") {
                    VisionGLSurfaceView.visionMode = VisionMode.Boiler;
                }else if(message.getMessage() == "lift"){
                    VisionGLSurfaceView.visionMode = VisionMode.Lift;
                }
            }



            Log.w("Connection" , message.getType() + " " + message.getMessage());
        }

        @Override
        public void run() {
            while(running) {
                if(socket != null || connected) {
                    BufferedReader reader;
                    try {
                        InputStream is = socket.getInputStream();
                        reader = new BufferedReader(new InputStreamReader(is));
                    } catch (IOException e) {
                        Log.e("ReadThread", "Could not get input stream");
                        continue;
                    } catch (NullPointerException npe) {
                        Log.e("ReadThread", "socket was null");
                        continue;
                    }

                    String jsonMessage = null;
                    try {
                        jsonMessage = reader.readLine();
                    } catch (IOException e) {
                    }
                    if (jsonMessage != null) {
                        OffWireMessage parsedMessage = new OffWireMessage(jsonMessage);
                        if (parsedMessage.isValid()) {
                            handleMessage(parsedMessage);
                        }
                    }
                } else {
                    try {
                        Thread.sleep(100, 0);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    // Thread to check connection is maintained through heartbeat messages
    protected class ConnectionMonitor implements Runnable {

        @Override
        public void run() {
            while(running) {
                try {
                    // If not connected, try to connect and wait for connection to establish
                    if(socket == null || !socket.isConnected() && !connected) {
                        tryConnect();
                        Thread.sleep(250, 0);
                    }

                    long now = System.currentTimeMillis();

                    // Send a heartbeat message if it has been long enough
                    if(now - lastHeartbeatSent > SEND_HEARTBEAT_PERIOD) {
                        send(HeartbeatMessage.getInstance());
                        lastHeartbeatSent = now;
                    }

                    // Update connected status if it is incorrect (response taking longer than threshold)
                    if(Math.abs(lastHeartbeatReceived - lastHeartbeatSent) > THRESHOLD_HEARTBEAT && connected) {
                        connected = false;
                        broadcastRobotDisconnected();
                    }

                    if(Math.abs(lastHeartbeatReceived - lastHeartbeatSent) < THRESHOLD_HEARTBEAT && !connected) {
                        connected = true;
                        broadcastRobotConnected();
                    }

                    Thread.sleep(CONNECTOR_SLEEP_MS, 0);
                } catch (InterruptedException e) {

                }
            }
        }
    }

    public RobotConnection(Context context, String host, int port) {
        this.context = context;
        this.host = host;
        this.port = port;
    }

    public RobotConnection(Context context) {
        this(context, ROBOT_PROXY_HOST, ROBOT_PORT);
    }

    synchronized private void tryConnect() {
        if(socket == null) {
            try {
                socket = new Socket(host, port);
            } catch (IOException e) {
                Log.w("RobotConnector", "Could not connect");
                socket = null;
            }
        }
    }

    // Stopping all threads and waiting for them to complete their cycles
    synchronized public void stop() {
        running = false;

        if(connectThread != null && connectThread.isAlive()) {
            try {
                connectThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(writeThread != null && writeThread.isAlive()) {
            try {
                writeThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(readThread != null && readThread.isAlive()) {
            try {
                readThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    synchronized public void start() {
        running  = true;

        if(writeThread == null || !writeThread.isAlive()) {
            writeThread = new Thread(new WriteThread());
            writeThread.start();
        }

        if(readThread == null || !readThread.isAlive()) {
            readThread = new Thread(new ReadThread());
            readThread.start();
        }

        if(connectThread == null || !connectThread.isAlive()) {
            connectThread = new Thread(new ConnectionMonitor());
            connectThread.start();
        }
    }

    synchronized public void restart() {
        stop();
        start();
    }

    synchronized public boolean isConnected() {
        return socket != null && socket.isConnected() && connected;
    }

    // Converts VisionMessage to bytes to be sent in output stream to roboRIO
    private synchronized boolean sendToWire(VisionMessage message) {
        String toSend= message.toJson() + "\n";
        if(socket != null && socket.isConnected()) {
            try {
                OutputStream os = socket.getOutputStream();
                os.write(toSend.getBytes());
                return true;
            } catch(IOException e) {
                Log.w("RobotConnection", "Could not send data to socket, try to reconnect");
                socket = null;
            }
        }

        return false;
    }

    public synchronized boolean send(VisionMessage message) {
        Log.w("RobotConnection", "Message sent(type: " + message.getType() + ", message: " + message.getMessage() + ")");
        return toSend.offer(message);
    }

    public void broadcastRobotConnected() {
        Intent i = new Intent(RobotConnectionStatusBroadcastReceiver.ACTION_ROBOT_CONNECTED);
        context.sendBroadcast(i);
    }

    public void broadcastRobotDisconnected() {
        Intent i = new Intent(RobotConnectionStatusBroadcastReceiver.ACTION_ROBOT_DISCONNECTED);
        context.sendBroadcast(i);
    }
}
