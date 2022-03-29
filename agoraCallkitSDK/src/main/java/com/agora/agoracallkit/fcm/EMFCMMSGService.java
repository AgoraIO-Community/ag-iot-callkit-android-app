package com.agora.agoracallkit.fcm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.agora.agoracallkit.logger.ALog;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class EMFCMMSGService extends FirebaseMessagingService {
    private static final String TAG = "CALLKIT/EMFCMMSGSrv";
    private NotificationManager notificationManager = null;
    protected static int NOTIFY_ID = 0123; // start notification id
    private static final String CHANNEL_ID = "hyphenate_easeim_notification";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        if (remoteMessage.getData().size() > 0) {
            String message = remoteMessage.getData().get("alert");
            ALog.getInstance().i(TAG, "<onMessageReceived> " + message);
            createNotify(message);
        }
    }

    private void createNotify(String message){
        notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            // Create the notification channel for Android 8.0
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "FCM Channel", NotificationManager.IMPORTANCE_HIGH);
            // 通知级别
            channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
            // 开启震动
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500});
            notificationManager.createNotificationChannel(channel);
        }
        Intent intent = this.getPackageManager().getLaunchIntentForPackage(getApplicationInfo().packageName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), NOTIFY_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setFullScreenIntent(pendingIntent, true)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("消息通知")
                .setTicker(message)
                .setContentText(message)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        Notification notification = builder.build();
        notificationManager.notify(NOTIFY_ID, notification);
    }
}
