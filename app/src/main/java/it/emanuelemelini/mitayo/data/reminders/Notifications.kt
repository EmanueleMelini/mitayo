package it.emanuelemelini.mitayo.data.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Notification permission still to be asked (it exists from Android 13). */
fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** Permission granted and notifications not turned off in the system settings. */
fun notificationsAllowed(context: Context): Boolean =
    !needsNotificationPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()
