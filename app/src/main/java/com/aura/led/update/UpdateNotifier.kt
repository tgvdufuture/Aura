package com.aura.led.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aura.led.MainActivity
import com.aura.led.R

/**
 * The notification that carries a new release to a user who has not opened the app.
 *
 * Quiet on purpose: it lands in the shade — and on the lock screen — without a banner or a sound,
 * because a new version is news, not an interruption. Tapping it opens Aura on the update screen;
 * the download and the install confirmation stay where they were written and verified.
 */
object UpdateNotifier {
    const val CHANNEL_ID = "aura_updates"

    /** Read by [MainActivity] to open the update screen straight away instead of the app list. */
    const val EXTRA_SHOW_UPDATE = "com.aura.led.extra.SHOW_UPDATE"

    private const val TAG = "AuraUpdate"
    private const val NOTIFICATION_ID = 2
    private const val REQUEST_CODE = 2027

    fun notify(context: Context, update: PendingUpdate) {
        val manager = NotificationManagerCompat.from(context)
        // Android 13+ can refuse to post without POST_NOTIFICATIONS; the check still ran, and the
        // update screen stays reachable from the app, so a refusal is logged and nothing else.
        if (!manager.areNotificationsEnabled()) {
            Log.d(TAG, "notifications disabled -> update ${update.tag} not announced")
            return
        }
        createChannel(context)
        runCatching {
            manager.notify(NOTIFICATION_ID, build(context, update))
        }.onFailure { Log.w(TAG, "could not post the update notification", it) }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
            .onFailure { Log.w(TAG, "could not cancel the update notification", it) }
    }

    private fun build(context: Context, update: PendingUpdate) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_led)
            .setContentTitle(context.getString(R.string.update_notification_title, update.versionName))
            .setContentText(context.getString(R.string.update_notification_text))
            .setContentIntent(openUpdateScreen(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            // The channel is already quiet; this also keeps the notification silent on the public
            // version of the channel, where importance is the user's own choice.
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    private fun openUpdateScreen(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(EXTRA_SHOW_UPDATE)
            .putExtra(EXTRA_SHOW_UPDATE, true)
            // Bring the running app forward instead of stacking a second copy of the activity:
            // an app already open on another screen receives this through onNewIntent.
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Importance DEFAULT: sits in the shade and on the lock screen, never as a heads-up banner.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.update_channel_description)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }
}
