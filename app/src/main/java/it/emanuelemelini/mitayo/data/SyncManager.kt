package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.mal.MalApi
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SyncState {
    data object Idle : SyncState

    data object UserList : SyncState

    /** [loaded] of [total] catalog anime downloaded. */
    data class Catalog(val loaded: Int, val total: Int) : SyncState

    data class Failed(val error: Throwable) : SyncState
}

/** Brings the user's list and the catalog up to date, one request at a time. */
class SyncManager(
    private val api: MalApi,
    private val listRepository: ListRepository,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private var job: Job? = null

    /** At every start: only what is old. */
    fun syncIfStale() = start(forceList = false)

    /** After the login and on request: the list always, the catalog if it's old. */
    fun syncNow() = start(forceList = true)

    fun cancel() {
        job?.cancel()
        _state.value = SyncState.Idle
    }

    private fun start(forceList: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                val info = settingsRepository.currentSyncInfo()
                val now = System.currentTimeMillis()
                if (forceList || now - info.listSyncedAt > LIST_MAX_AGE_MS) {
                    _state.value = SyncState.UserList
                    val user = api.me()
                    settingsRepository.setUser(user.name, user.picture)
                    catalogRepository.saveListAnime(listRepository.syncFromMal())
                    settingsRepository.setListSyncedAt(now)
                }
                if (info.catalogNextOffset == 0 || now - info.catalogSyncedAt > CATALOG_MAX_AGE_MS) {
                    _state.value = SyncState.Catalog(0, CatalogRepository.INITIAL_SIZE)
                    catalogRepository.refresh { loaded, total -> _state.value = SyncState.Catalog(loaded, total) }
                }
                _state.value = SyncState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SyncState.Failed(e)
            }
        }
    }

    private companion object {
        const val LIST_MAX_AGE_MS = 10 * 60 * 1000L
        /** Daily: anime removed from MAL must leave the device within 24 hours (API agreement 3(e)). */
        const val CATALOG_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
