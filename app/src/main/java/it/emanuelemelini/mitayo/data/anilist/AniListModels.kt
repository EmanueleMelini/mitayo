package it.emanuelemelini.mitayo.data.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The logged-in AniList user. */
@Serializable
data class AniListUser(
    val id: Int,
    val name: String,
    val avatar: AniListAvatar? = null,
    val options: AniListUserOptions? = null,
    val mediaListOptions: AniListListOptions? = null,
)

/** Off, AniList hides 18+ anime from the user's requests: Mitayo's too. */
@Serializable
data class AniListUserOptions(val displayAdultContent: Boolean = false)

@Serializable
data class AniListAvatar(val medium: String? = null)

@Serializable
data class AniListListOptions(val scoreFormat: String? = null)

@Serializable
internal data class ViewerData(@SerialName("Viewer") val viewer: AniListUser)

/** An anime of the user's AniList list. */
@Serializable
data class AniListListEntry(
    /** The entry, needed to remove it. */
    val id: Int,
    val status: String? = null,
    val progress: Int? = null,
    /**
     * score(format: POINT_100). The score in the user's format isn't asked in the same request:
     * AniList then mixes up the two on some entries.
     */
    val scoreRaw: Int? = null,
    val media: AniListMedia,
)

@Serializable
data class AniListMedia(
    val id: Int,
    /** null for the anime AniList has and MAL hasn't. */
    val idMal: Int? = null,
    val title: AniListTitle? = null,
)

@Serializable
data class AniListTitle(val romaji: String? = null)

@Serializable
internal data class ListCollectionData(@SerialName("MediaListCollection") val collection: ListCollection? = null)

@Serializable
internal data class ListCollection(
    val lists: List<ListGroup> = emptyList(),
    val hasNextChunk: Boolean = false,
)

@Serializable
internal data class ListGroup(val entries: List<AniListListEntry> = emptyList())

@Serializable
internal data class MediaPageData(@SerialName("Page") val page: MediaPage)

@Serializable
internal data class MediaPage(val media: List<AniListMedia> = emptyList())

/** What AniList stored after a change: the entry without its media. */
@Serializable
data class SavedEntry(
    val id: Int,
    val status: String? = null,
    val progress: Int? = null,
    val scoreRaw: Int? = null,
)

@Serializable
internal data class SaveData(@SerialName("SaveMediaListEntry") val saved: SavedEntry)
