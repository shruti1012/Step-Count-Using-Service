package simplepedometer.apps.android.google.om.myapplication;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by wildcoder on 03/11/16.
 */

public class MyService extends Service implements StepListener, SensorEventListener {
    public static final int NOTIF_ID = 101;
    public static final int SET_COUNT = 1;
    public static final int STOP_FOREGROUND = 2;
    public static final int MSG_REGISTER_CLIENT = 3;
    int mValue = 0;
    /**
     * Command to the service to unregister a client, ot stop receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client as previously given with MSG_REGISTER_CLIENT.
     */
    public static final int MSG_UNREGISTER_CLIENT = 4;

    @Override
    public void onCreate() {
        initSensor();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // This always shows up in the notifications area when this Service is running.
        // TODO: String localization
        Notification notification = getNotification();
        startForeground(NOTIF_ID, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Tell the user we stopped.
        sensorManager.unregisterListener(this);
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler());

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private Notification getNotification() {
        CharSequence text = getText(R.string.remote_service_started);
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MessengerServiceActivities.class), 0);


        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setTicker(text)
                        .setWhen(System.currentTimeMillis())
                        .setContentTitle("Step Count:" + numSteps)
                        .setContentText(text)
                        .setContentIntent(contentIntent);

        return mBuilder.build();
    }


    //write code of pedometer
    private SimpleStepDetector simpleStepDetector;
    private SensorManager sensorManager;
    private Sensor accel;
    private int numSteps;

    private void initSensor() {
        // Get an instance of the SensorManager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        simpleStepDetector = new SimpleStepDetector();
        simpleStepDetector.registerListener(this);
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void step(long timeNs) {
        numSteps++;
        Notification notification = getNotification();
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIF_ID, notification);
        Message message = new Message();
        message.what = SET_COUNT;
        message.arg1 = numSteps;
        try {
            mMessenger.send(message);
        } catch (RemoteException e) {
            Log.e("", "steps error:" + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            simpleStepDetector.updateAccel(
                    event.timestamp, event.values[0], event.values[1], event.values[2]);
        }
    }

    ArrayList<Messenger> mClients = new ArrayList<Messenger>();

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.e("", "steps received in service:" + msg.what);
            Log.e("", "steps arg in service: " + msg.arg1);
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;

                case STOP_FOREGROUND:
                    stopForeground(true);
                    break;
                case SET_COUNT:
                    mValue = msg.arg1;
                    for (int i = mClients.size() - 1; i >= 0; i--) {
                        try {
                            mClients.get(i).send(Message.obtain(null,
                                    SET_COUNT, mValue, 0));
                        } catch (RemoteException e) {
                            // The client is dead.  Remove it from the list;
                            // we are going through the list from back to front
                            // so this is safe to do inside the loop.
                            mClients.remove(i);
                        }
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

}
