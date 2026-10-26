package it.emanuelemelini.mitayo.data.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import it.emanuelemelini.mitayo.BuildConfig

/**
 * Firebase is there only when the build had google-services.json (BuildConfig.FIREBASE): a fork
 * without it builds and runs with statistics, crash reports and news off. Without the file
 * Firebase isn't initialized and every getInstance() would throw.
 */
object FirebaseSupport {
    fun available(context: Context): Boolean = BuildConfig.FIREBASE && FirebaseApp.getApps(context).isNotEmpty()
}
