package it.emanuelemelini.mitayo.data.update

/** When the "new version" notification is worth showing. */
object UpdateNotice {

    /**
     * Only for a release newer than the installed version, and once per version: [lastNotified]
     * is the last one already announced.
     */
    fun shouldNotify(current: AppVersion?, latest: AppVersion?, lastNotified: AppVersion?): Boolean =
        current != null && latest != null && latest > current && (lastNotified == null || latest > lastNotified)
}
