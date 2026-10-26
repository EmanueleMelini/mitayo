package it.emanuelemelini.mitayo.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.ui.categories.CategoriesScreen
import it.emanuelemelini.mitayo.ui.compare.CompareScreen
import it.emanuelemelini.mitayo.ui.compare.UserDeckScreen
import it.emanuelemelini.mitayo.ui.deck.DeckScreen
import it.emanuelemelini.mitayo.ui.profile.ProfilePage
import it.emanuelemelini.mitayo.ui.profile.ProfilePageScreen
import it.emanuelemelini.mitayo.ui.profile.ProfileScreen
import it.emanuelemelini.mitayo.ui.search.SearchScreen
import it.emanuelemelini.mitayo.ui.skipped.SkippedScreen
import kotlinx.serialization.Serializable

@Serializable
object CategoriesRoute

@Serializable
object ProfileRoute

/** [categoryKey]: a Category key. */
@Serializable
data class DeckRoute(val categoryKey: String)

/** [page]: a ProfilePage name. */
@Serializable
data class ProfilePageRoute(val page: String)

@Serializable
object SkippedRoute

@Serializable
object SearchRoute

@Serializable
object CompareRoute

@Serializable
object UserDeckRoute

private enum class TopLevel(val route: Any, @StringRes val labelRes: Int, val icon: ImageVector) {
    CATEGORIES(CategoriesRoute, R.string.nav_categories, Icons.Rounded.GridView),
    PROFILE(ProfileRoute, R.string.nav_profile, Icons.Rounded.Person),
}

/** [launchRequest]: where a tapped notification goes; [onLaunchHandled] once it's there. */
@Composable
fun MitayoNavHost(launchRequest: LaunchRequest?, onLaunchHandled: () -> Unit) {
    val navController = rememberNavController()
    val telemetry = appContainer().telemetry
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { telemetry.logScreen(screenName(it.destination)) }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    // The deck is full screen: the bottom bar is only on the three main screens
    val showBottomBar = destination == null || destination.isTopLevel()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Slides away under the deck and the Profile pages, and back
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(navTween()) { it } + fadeIn(navTween()),
                exit = slideOutVertically(navTween()) { it } + fadeOut(navTween()),
            ) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination?.hasRoute(item.route::class) == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Material shared axis: a new screen comes in from the right, Back goes the other way;
        // between the bottom bar tabs only a fade
        NavHost(
            navController = navController,
            startDestination = CategoriesRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = {
                if (isTabSwitch()) fadeIn(navTween()) else slideInHorizontally(navTween()) { it / SLIDE_FRACTION } + fadeIn(navTween())
            },
            exitTransition = {
                if (isTabSwitch()) fadeOut(navTween()) else slideOutHorizontally(navTween()) { -it / SLIDE_FRACTION } + fadeOut(navTween())
            },
            popEnterTransition = {
                if (isTabSwitch()) fadeIn(navTween()) else slideInHorizontally(navTween()) { -it / SLIDE_FRACTION } + fadeIn(navTween())
            },
            popExitTransition = {
                if (isTabSwitch()) fadeOut(navTween()) else slideOutHorizontally(navTween()) { it / SLIDE_FRACTION } + fadeOut(navTween())
            },
        ) {
            composable<CategoriesRoute> {
                CategoriesScreen(
                    onOpenCategory = { navController.navigate(DeckRoute(it.key)) },
                    onOpenSearch = { navController.navigate(SearchRoute) },
                    onOpenCompare = { navController.navigate(CompareRoute) },
                    onOpenUserDeck = { navController.navigate(UserDeckRoute) },
                )
            }
            composable<ProfileRoute> {
                ProfileScreen(onOpenPage = { navController.navigate(ProfilePageRoute(it.name)) })
            }
            composable<ProfilePageRoute> { entry ->
                ProfilePageScreen(
                    page = ProfilePage.valueOf(entry.toRoute<ProfilePageRoute>().page),
                    onBack = { navController.popBackStack() },
                    onOpenSkipped = { navController.navigate(SkippedRoute) },
                )
            }
            composable<SkippedRoute> {
                SkippedScreen(onBack = { navController.popBackStack() })
            }
            composable<SearchRoute> {
                SearchScreen(onBack = { navController.popBackStack() })
            }
            composable<CompareRoute> {
                CompareScreen(onBack = { navController.popBackStack() })
            }
            composable<UserDeckRoute> {
                UserDeckScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDeck = { navController.navigate(DeckRoute(it.key)) },
                )
            }
            composable<DeckRoute> { entry ->
                DeckScreen(
                    categoryKey = entry.toRoute<DeckRoute>().categoryKey,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        // After the NavHost: its graph is set
        LaunchedEffect(launchRequest) {
            when (launchRequest) {
                null, LaunchRequest.ShowUpdate -> return@LaunchedEffect
                LaunchRequest.OpenCategories -> navController.navigate(CategoriesRoute) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
                is LaunchRequest.OpenDeck -> if (Category.fromKey(launchRequest.categoryKey) != null) {
                    navController.navigate(DeckRoute(launchRequest.categoryKey)) { launchSingleTop = true }
                }
                LaunchRequest.OpenSearch -> navController.navigate(SearchRoute) { launchSingleTop = true }
            }
            onLaunchHandled()
        }
    }
}

/** Fixed names for the statistics: never the route arguments (which deck, which anime). */
private fun screenName(destination: NavDestination): String = when {
    destination.hasRoute(CategoriesRoute::class) -> "categories"
    destination.hasRoute(ProfileRoute::class) -> "profile"
    destination.hasRoute(ProfilePageRoute::class) -> "profile_page"
    destination.hasRoute(SkippedRoute::class) -> "skipped"
    destination.hasRoute(SearchRoute::class) -> "search"
    destination.hasRoute(CompareRoute::class) -> "compare"
    destination.hasRoute(UserDeckRoute::class) -> "user_deck"
    destination.hasRoute(DeckRoute::class) -> "deck"
    else -> "other"
}

private fun NavDestination.isTopLevel(): Boolean = TopLevel.entries.any { hasRoute(it.route::class) }

/** From one bottom bar tab to the other: no slide, they're side by side. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.isTopLevel() && targetState.destination.isTopLevel()

private fun <T> navTween() = tween<T>(durationMillis = 300, easing = FastOutSlowInEasing)

/** How far the screens slide: a fifth of the width, the rest is the fade. */
private const val SLIDE_FRACTION = 5
