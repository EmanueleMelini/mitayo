package it.emanuelemelini.mitayo.data

/** The title languages MAL gives for every anime: the main title (usually romaji), English and Japanese. */
enum class TitleLanguage { ROMAJI, ENGLISH, JAPANESE }

/**
 * What the cards show: the title in [title] (romaji when MAL hasn't that one) and, unless null,
 * [subtitle] under it (left out when MAL hasn't it).
 */
data class TitleChoice(
    val title: TitleLanguage = TitleLanguage.ROMAJI,
    val subtitle: TitleLanguage? = TitleLanguage.JAPANESE,
)
