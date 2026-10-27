package it.emanuelemelini.mitayo.ui.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.SportsMotorsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.AnimeSeason
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.ui.components.listStatusIcon
import it.emanuelemelini.mitayo.ui.components.listStatusLabel
import it.emanuelemelini.mitayo.ui.components.seasonName

fun Category.icon(): ImageVector = when (this) {
    Category.Popular -> Icons.Rounded.LocalFireDepartment
    Category.TopRated -> Icons.Rounded.Star
    Category.Airing -> Icons.Rounded.LiveTv
    Category.Season -> Icons.Rounded.CalendarMonth
    Category.Upcoming -> Icons.Rounded.Upcoming
    is Category.OfSeason -> Icons.Rounded.EditCalendar
    Category.Movies -> Icons.Rounded.Movie
    Category.Suggested -> Icons.Rounded.Recommend
    is Category.OfGenre -> genreIcon(genreId)
    is Category.OfDecade -> Icons.Rounded.History
    is Category.OfUser -> Icons.Rounded.Groups
    is Category.OfStatus -> listStatusIcon(status)
}

/** [genreName]: name of the MAL genre, for [Category.OfGenre]. */
@Composable
fun Category.title(genreName: String? = null): String = when (this) {
    Category.Popular -> stringResource(R.string.category_popular)
    Category.TopRated -> stringResource(R.string.category_top_rated)
    Category.Airing -> stringResource(R.string.category_airing)
    Category.Season -> {
        val season = AnimeSeason.current()
        stringResource(R.string.category_season, seasonName(season.season).orEmpty(), season.year)
    }
    is Category.OfSeason -> stringResource(R.string.category_season, seasonName(season).orEmpty(), year)
    Category.Upcoming -> stringResource(R.string.category_upcoming)
    Category.Movies -> stringResource(R.string.category_movies)
    Category.Suggested -> stringResource(R.string.category_suggested)
    is Category.OfGenre -> genreName.orEmpty()
    is Category.OfUser -> stringResource(R.string.category_user, userName)
    is Category.OfStatus -> listStatusLabel(status)
    is Category.OfDecade -> if (start == Category.OfDecade.BEFORE) {
        stringResource(R.string.category_before, Category.OfDecade.FIRST)
    } else {
        stringResource(R.string.category_decade, start)
    }
}

/** Icons for the MAL genre, theme and demographic ids; the rarer ones share a generic one. */
fun genreIcon(genreId: Int): ImageVector = when (genreId) {
    1 -> Icons.Rounded.Bolt // Action
    2 -> Icons.Rounded.Explore // Adventure
    4 -> Icons.Rounded.SentimentVerySatisfied // Comedy
    5 -> Icons.Rounded.Palette // Avant Garde
    7 -> Icons.Rounded.Search // Mystery
    8 -> Icons.Rounded.TheaterComedy // Drama
    9 -> Icons.Rounded.Whatshot // Ecchi
    10 -> Icons.Rounded.AutoAwesome // Fantasy
    12, 49 -> Icons.Rounded.Explicit // Hentai, Erotica
    14 -> Icons.Rounded.DarkMode // Horror
    22 -> Icons.Rounded.Favorite // Romance
    24 -> Icons.Rounded.RocketLaunch // Sci-Fi
    26, 28 -> Icons.Rounded.FavoriteBorder // Girls Love, Boys Love
    30 -> Icons.Rounded.SportsSoccer // Sports
    36 -> Icons.Rounded.LocalCafe // Slice of Life
    37 -> Icons.Rounded.AutoFixHigh // Supernatural
    41 -> Icons.Rounded.HourglassTop // Suspense
    46 -> Icons.Rounded.EmojiEvents // Award Winning
    47 -> Icons.Rounded.Restaurant // Gourmet
    3 -> Icons.Rounded.SportsMotorsports // Racing
    6 -> Icons.Rounded.AutoStories // Mythology
    11 -> Icons.Rounded.Extension // Strategy Game
    13 -> Icons.Rounded.HistoryEdu // Historical
    17, 54 -> Icons.Rounded.SportsMma // Martial Arts, Combat Sports
    18 -> Icons.Rounded.SmartToy // Mecha
    19 -> Icons.Rounded.MusicNote // Music
    20 -> Icons.Rounded.Masks // Parody
    23 -> Icons.Rounded.School // School
    29 -> Icons.Rounded.Public // Space
    31 -> Icons.Rounded.FlashOn // Super Power
    32, 58 -> Icons.Rounded.Bloodtype // Vampire, Gore
    35, 73 -> Icons.Rounded.Groups // Harem, Reverse Harem
    38 -> Icons.Rounded.MilitaryTech // Military
    39 -> Icons.Rounded.Fingerprint // Detective
    40 -> Icons.Rounded.Psychology // Psychological
    48 -> Icons.Rounded.Work // Workplace
    51, 71 -> Icons.Rounded.Pets // Anthropomorphic, Pets
    52 -> Icons.Rounded.LocalFlorist // CGDCT
    53 -> Icons.Rounded.ChildCare // Childcare
    55 -> Icons.Rounded.TwoWheeler // Delinquents
    56 -> Icons.Rounded.AutoStories // Educational
    57 -> Icons.Rounded.SentimentVerySatisfied // Gag Humor
    59 -> Icons.Rounded.Casino // High Stakes Game
    60, 61, 75 -> Icons.Rounded.Mic // Idols, Showbiz
    62 -> Icons.Rounded.DoorFront // Isekai
    63 -> Icons.Rounded.Spa // Iyashikei
    64 -> Icons.Rounded.FavoriteBorder // Love Polygon
    66 -> Icons.Rounded.AutoAwesome // Mahou Shoujo
    67 -> Icons.Rounded.MedicalServices // Medical
    68 -> Icons.Rounded.Gavel // Organized Crime
    69 -> Icons.Rounded.Computer // Otaku Culture
    70 -> Icons.Rounded.TheaterComedy // Performing Arts
    72 -> Icons.Rounded.Autorenew // Reincarnation
    76 -> Icons.Rounded.Park // Survival
    77 -> Icons.Rounded.SportsBasketball // Team Sports
    78 -> Icons.Rounded.History // Time Travel
    79 -> Icons.Rounded.SportsEsports // Video Game
    80 -> Icons.Rounded.Brush // Visual Arts
    82 -> Icons.Rounded.LocationCity // Urban Fantasy
    15 -> Icons.Rounded.ChildCare // Kids
    25 -> Icons.Rounded.LocalFlorist // Shoujo
    27 -> Icons.Rounded.SportsMartialArts // Shounen
    42 -> Icons.Rounded.Person // Seinen
    43 -> Icons.Rounded.Face // Josei
    50 -> Icons.Rounded.People // Adult Cast
    else -> Icons.Rounded.Style
}
