package ru.btcradar.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.btcradar.MainActivity
import ru.btcradar.model.AlertHit

/** Канал + постинг уведомлений о сработавших курсах. */
object Notifier {

    private const val CHANNEL_ID = "rate_alerts"
    private const val CHANNEL_NAME = "Курсы и алерты"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления, когда курс обменника пробивает заданный порог"
            }
            ctx.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(ch)
        }
    }

    /** Разрешены ли уведомления (Android 13+ требует рантайм-разрешения). */
    fun areEnabled(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun notifyHit(ctx: Context, hit: AlertHit) {
        ensureChannel(ctx)
        if (!areEnabled(ctx)) return

        val tap = PendingIntent.getActivity(
            ctx, hit.alert.id.hashCode(),
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Сработал курс")
            .setContentText(hit.shortText())
            .setStyle(NotificationCompat.BigTextStyle().bigText(hit.shortText()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        runCatching {
            NotificationManagerCompat.from(ctx).notify(hit.alert.id.hashCode(), n)
        }
    }
}
