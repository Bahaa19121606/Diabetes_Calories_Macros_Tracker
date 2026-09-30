package com.example.healthtracker.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.healthtracker.navigation.DeepLinks
import com.example.healthtracker.navigation.Routes
import com.example.healthtracker.ui.bolus.BolusCalculatorScreen
import com.example.healthtracker.ui.calibration.CalibrationScreen
import com.example.healthtracker.ui.calibration.CarbRatioTestScreen
import com.example.healthtracker.ui.common.formatDuration
import com.example.healthtracker.ui.meal.MealScreen
import com.example.healthtracker.ui.profile.ProfileScreen
import com.example.healthtracker.ui.review.ReviewsScreen
import com.example.healthtracker.ui.sleep.SleepLogScreen
import com.example.healthtracker.ui.t1d.GlucoseLogScreen
import com.example.healthtracker.ui.t1d.InsulinLogScreen
import com.example.healthtracker.ui.theme.AppColors
import com.example.healthtracker.ui.timeline.TimelineScreen
import com.example.healthtracker.ui.workout.WorkoutLogScreen
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY(Routes.TODAY, "Today", Icons.Outlined.Today),
    REVIEWS(Routes.REVIEWS, "Reviews", Icons.Outlined.BarChart),
    INSIGHTS(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights),
    SETTINGS(Routes.PROFILE, "Settings", Icons.Outlined.Settings),
}

/** Screens reached from a tab or the + menu; they get a back arrow and a title. */
private val SUB_SCREEN_TITLES = mapOf(
    Routes.LOG_MEAL to "Log a meal",
    Routes.LOG_GLUCOSE to "Log glucose",
    Routes.LOG_INSULIN to "Log insulin",
    Routes.LOG_SLEEP to "Log sleep",
    Routes.LOG_WORKOUT to "Log a workout",
    Routes.BOLUS to "Dose calculator",
    Routes.CARB_TEST to "Carb ratio test",
)

private data class QuickLog(val label: String, val icon: ImageVector, val color: Color, val route: String)

@Composable
private fun quickLogs() = listOf(
    QuickLog("Log meal", Icons.Outlined.Restaurant, AppColors.Carbs, Routes.LOG_MEAL),
    QuickLog("Log glucose", Icons.Outlined.WaterDrop, AppColors.InRange, Routes.LOG_GLUCOSE),
    QuickLog("Log insulin", Icons.Outlined.Vaccines, AppColors.Violet, Routes.LOG_INSULIN),
    QuickLog("Log sleep", Icons.Outlined.Bedtime, AppColors.SleepAccent, Routes.LOG_SLEEP),
    QuickLog("Log workout", Icons.Outlined.FitnessCenter, AppColors.Warning, Routes.LOG_WORKOUT),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthTrackerRoot() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    // Cold starts are handled by NavHost itself; this covers deep links delivered to the
    // already-running singleTop activity.
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { intent -> navController.handleDeepLink(intent) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore('?')
    val subScreenTitle = SUB_SCREEN_TITLES[currentRoute]
    val isTopLevel = Tab.entries.any { it.route == currentRoute }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (subScreenTitle != null) {
                    TopAppBar(
                        title = { Text(subScreenTitle, style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = {
                            IconButton(onClick = { navController.navigateUp() }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                }
            },
            bottomBar = {
                if (isTopLevel) BottomBar(currentRoute) { tab -> navController.navigateToTab(tab.route) }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.TODAY,
                // Top-level screens draw edge to edge and pad their own content.
                modifier = if (isTopLevel) Modifier else Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            ) {
                composable(Routes.TODAY) {
                    TimelineScreen(
                        onOpenReview = { navController.navigateToTab(Routes.REVIEWS) },
                        contentPadding = padding,
                    )
                }
                composable(Routes.REVIEWS) { ReviewsScreen(contentPadding = padding) }
                composable(Routes.INSIGHTS) {
                    CalibrationScreen(
                        snackbarHostState,
                        onOpenCarbTest = { navController.navigate(Routes.CARB_TEST) },
                        contentPadding = padding,
                    )
                }
                composable(Routes.LOG_MEAL) {
                    MealScreen(snackbarHostState, onCalculateDose = { navController.navigate(Routes.bolus(it)) })
                }
                composable(
                    route = Routes.LOG_GLUCOSE_WITH_ARGS,
                    arguments = listOf(
                        navArgument(DeepLinks.ARG_CONTEXT) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.LOG_GLUCOSE_PATTERN }),
                ) {
                    GlucoseLogScreen(snackbarHostState)
                }
                composable(Routes.LOG_INSULIN) {
                    InsulinLogScreen(snackbarHostState, onOpenDoseCalculator = { navController.navigate(Routes.bolus()) })
                }
                composable(Routes.LOG_SLEEP) {
                    SleepLogScreen(
                        onSaved = { session ->
                            navController.navigateUp()
                            scope.launch { snackbarHostState.showSnackbar("Logged ${formatDuration(session.duration)} of sleep") }
                        },
                    )
                }
                composable(Routes.LOG_WORKOUT) { WorkoutLogScreen(snackbarHostState) }
                composable(Routes.PROFILE) { ProfileScreen(snackbarHostState, contentPadding = padding) }
                composable(
                    route = Routes.BOLUS_WITH_ARGS,
                    arguments = listOf(
                        navArgument(Routes.ARG_MEAL_ID) {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                    ),
                ) {
                    BolusCalculatorScreen(
                        snackbarHostState,
                        onDone = { navController.navigateUp() },
                        onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    )
                }
                composable(
                    route = Routes.CARB_TEST,
                    deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.CARB_TEST_PATTERN }),
                ) {
                    CarbRatioTestScreen(snackbarHostState)
                }
            }
        }

        if (isTopLevel) {
            QuickLogMenu(
                open = menuOpen,
                onToggle = { menuOpen = !menuOpen },
                onSelect = { route ->
                    menuOpen = false
                    navController.navigate(route)
                },
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private val BAR_HEIGHT = 76.dp
private val FAB_SIZE = 72.dp

@Composable
private fun BottomBar(currentRoute: String?, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.Border)
            Row(Modifier.fillMaxWidth().height(BAR_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                val (left, right) = Tab.entries.chunked(2)
                left.forEach { TabItem(it, currentRoute == it.route, Modifier.weight(1f), onSelect) }
                // The + button sits in this gap.
                Spacer(Modifier.width(FAB_SIZE + 16.dp))
                right.forEach { TabItem(it, currentRoute == it.route, Modifier.weight(1f), onSelect) }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onSelect: (Tab) -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(CircleShape)
            .clickable(role = Role.Tab, onClick = { onSelect(tab) })
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** The omnipresent + button; it opens a menu of big, easy-to-hit logging shortcuts. */
@Composable
private fun QuickLogMenu(open: Boolean, onToggle: () -> Unit, onSelect: (String) -> Unit) {
    val rotation by animateFloatAsState(if (open) 45f else 0f, label = "fabRotation")
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AppColors.Scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
            )
        }
        Column(
            // The button sits centered in the bottom bar's gap; the menu opens above it.
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = (BAR_HEIGHT - FAB_SIZE) / 2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(open, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
                Column(
                    Modifier.padding(bottom = 20.dp).widthIn(min = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    quickLogs().forEach { item -> QuickLogItem(item, onClick = { onSelect(item.route) }) }
                }
            }
            Box(
                Modifier
                    .size(FAB_SIZE)
                    .clip(CircleShape)
                    .background(AppColors.AiGradient)
                    .clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Add an entry", onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = if (open) "Close" else "Add an entry",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp).rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun QuickLogItem(item: QuickLog, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = AppColors.Card,
        border = BorderStroke(0.5.dp, AppColors.Border),
        modifier = Modifier.widthIn(min = 260.dp).height(64.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(item.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = item.color)
            }
            Spacer(Modifier.width(16.dp))
            Text(item.label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

