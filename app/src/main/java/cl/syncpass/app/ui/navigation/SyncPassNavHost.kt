package cl.syncpass.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.syncpass.app.AppContainer
import cl.syncpass.app.ui.screens.AccreditScreen
import cl.syncpass.app.ui.screens.AnswerSurveyScreen
import cl.syncpass.app.ui.screens.CreateSurveyScreen
import cl.syncpass.app.ui.screens.EventDetailScreen
import cl.syncpass.app.ui.screens.EventFormScreen
import cl.syncpass.app.ui.screens.HomeScreen
import cl.syncpass.app.ui.screens.IndicatorsScreen
import cl.syncpass.app.ui.screens.ParticipantModeScreen
import cl.syncpass.app.ui.screens.ProjectsScreen
import cl.syncpass.app.ui.screens.QrScreen
import cl.syncpass.app.ui.screens.ScannerScreen
import cl.syncpass.app.ui.screens.SettingsScreen
import cl.syncpass.app.ui.screens.SurveyFormScreen
import cl.syncpass.app.ui.screens.SurveyResultsScreen
import cl.syncpass.app.ui.screens.SurveysScreen
import cl.syncpass.app.ui.screens.TakeSurveyScreen

object Routes {
    const val HOME = "home"
    const val ACCREDIT = "accredit"
    const val SURVEYS = "surveys"
    const val SETTINGS = "settings"
    const val EVENT_NEW = "event/new"
    const val EVENT_DETAIL = "event/{eventId}"
    const val EVENT_EDIT = "event/{eventId}/edit"
    const val EVENT_PARTICIPANT = "event/{eventId}/participant"
    const val QR = "qr/{participantId}"
    const val SURVEY = "survey/{attendanceId}"
    const val PARTICIPANT_SURVEY = "participant_survey/{attendanceId}"
    const val SURVEY_CREATE = "survey_create"
    const val SURVEY_TAKE = "survey_take"
    const val SURVEY_CUSTOM_ANSWER = "survey_custom_answer/{surveyId}"
    const val SURVEY_CUSTOM_RESULTS = "survey_custom_results/{surveyId}"
    const val INDICATORS = "indicators/{eventId}"
    const val SCANNER = "scanner/{eventId}/{useLocation}"
    const val PROJECTS = "projects"

    fun eventDetail(id: Long) = "event/$id"
    fun eventEdit(id: Long) = "event/$id/edit"
    fun accredit(eventId: Long? = null) =
        if (eventId == null) ACCREDIT else "$ACCREDIT?eventId=$eventId"

    fun qr(participantId: Long) = "qr/$participantId"
    fun survey(attendanceId: Long) = "survey/$attendanceId"
    fun participantSurvey(attendanceId: Long) = "participant_survey/$attendanceId"
    fun customSurveyAnswer(surveyId: Long) = "survey_custom_answer/$surveyId"
    fun customSurveyResults(surveyId: Long) = "survey_custom_results/$surveyId"
    fun indicators(eventId: Long) = "indicators/$eventId"
    fun scanner(eventId: Long, useLocation: Boolean) = "scanner/$eventId/$useLocation"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, "Inicio", Icons.Rounded.Home),
    TopLevelDestination(Routes.ACCREDIT, "Acreditar", Icons.Rounded.QrCodeScanner),
    TopLevelDestination(Routes.SETTINGS, "Ajustes", Icons.Rounded.Settings)
)

@Composable
fun SyncPassNavHost(container: AppContainer, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val baseRoute = currentRoute?.substringBefore("?")
    val isTopLevel = topLevelDestinations.any { it.route == baseRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (isTopLevel) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        topLevelDestinations.forEach { destination ->
                            NavigationBarItem(
                                selected = baseRoute == destination.route,
                                modifier = Modifier.testTag("nav_${destination.route}"),
                                onClick = {
                                    if (baseRoute != destination.route) {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label
                                    )
                                },
                                label = { Text(destination.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { rootPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = rootPadding.calculateBottomPadding())
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onEventClick = { navController.navigate(Routes.eventDetail(it)) },
                    onNewEvent = { navController.navigate(Routes.EVENT_NEW) },
                    onAccredit = { navController.navigate(Routes.ACCREDIT) },
                    onLogout = onLogout
                )
            }

            composable(Routes.ACCREDIT + "?eventId={eventId}",
                arguments = listOf(
                    navArgument("eventId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { entry ->
                val eventId = entry.arguments?.getLong("eventId")?.takeIf { it > 0 }
                AccreditScreen(
                    initialEventId = eventId,
                    onBack = if (eventId != null) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
                    onCreateEvent = { navController.navigate(Routes.EVENT_NEW) },
                    onScan = { id, useLocation ->
                        navController.navigate(Routes.scanner(id, useLocation))
                    },
                    onLogout = onLogout
                )
            }

            composable(Routes.SURVEYS) {
                SurveysScreen(
                    onCreateSurvey = { navController.navigate(Routes.SURVEY_CREATE) },
                    onTakeSurvey = { navController.navigate(Routes.SURVEY_TAKE) },
                    onOpenResults = { navController.navigate(Routes.customSurveyResults(it)) },
                    onLogout = onLogout
                )
            }

            composable(Routes.SURVEY_CREATE) {
                CreateSurveyScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(Routes.SURVEY_TAKE) {
                TakeSurveyScreen(
                    onBack = { navController.popBackStack() },
                    onCreateSurvey = { navController.navigate(Routes.SURVEY_CREATE) },
                    onAnswerSatisfaction = { navController.navigate(Routes.survey(it)) },
                    onAnswerCustom = { navController.navigate(Routes.customSurveyAnswer(it)) }
                )
            }

            composable(
                route = Routes.SURVEY_CUSTOM_ANSWER,
                arguments = listOf(navArgument("surveyId") { type = NavType.LongType })
            ) { entry ->
                val surveyId = entry.arguments?.getLong("surveyId") ?: return@composable
                AnswerSurveyScreen(
                    surveyId = surveyId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.SURVEY_CUSTOM_RESULTS,
                arguments = listOf(navArgument("surveyId") { type = NavType.LongType })
            ) { entry ->
                val surveyId = entry.arguments?.getLong("surveyId") ?: return@composable
                SurveyResultsScreen(
                    surveyId = surveyId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onProjects = { navController.navigate(Routes.PROJECTS) },
                    onLogout = onLogout
                )
            }

            composable(Routes.EVENT_NEW) {
                EventFormScreen(
                    eventId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onDeleted = {
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                )
            }

            composable(
                route = Routes.EVENT_EDIT,
                arguments = listOf(navArgument("eventId") { type = NavType.LongType })
            ) { entry ->
                val eventId = entry.arguments?.getLong("eventId") ?: return@composable
                EventFormScreen(
                    eventId = eventId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onDeleted = {
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                )
            }

            composable(
                route = Routes.EVENT_DETAIL,
                arguments = listOf(navArgument("eventId") { type = NavType.LongType })
            ) { entry ->
                val eventId = entry.arguments?.getLong("eventId") ?: return@composable
                EventDetailScreen(
                    eventId = eventId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.eventEdit(eventId)) },
                    onAccredit = { navController.navigate(Routes.accredit(eventId)) },
                    onOpenQr = { navController.navigate(Routes.qr(it)) }
                )
            }

            composable(
                route = Routes.EVENT_PARTICIPANT,
                arguments = listOf(navArgument("eventId") { type = NavType.LongType })
            ) { entry ->
                val eventId = entry.arguments?.getLong("eventId") ?: return@composable
                ParticipantModeScreen(
                    eventId = eventId,
                    onBack = { navController.popBackStack() },
                    onOpenSurvey = { navController.navigate(Routes.participantSurvey(it)) }
                )
            }

            composable(
                route = Routes.PARTICIPANT_SURVEY,
                arguments = listOf(navArgument("attendanceId") { type = NavType.LongType })
            ) { entry ->
                val attendanceId = entry.arguments?.getLong("attendanceId") ?: return@composable
                SurveyFormScreen(
                    attendanceId = attendanceId,
                    onBack = { navController.popBackStack() },
                    participantMode = true
                )
            }

            composable(
                route = Routes.QR,
                arguments = listOf(navArgument("participantId") { type = NavType.LongType })
            ) { entry ->
                val participantId = entry.arguments?.getLong("participantId") ?: return@composable
                QrScreen(
                    participantId = participantId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.SURVEY,
                arguments = listOf(navArgument("attendanceId") { type = NavType.LongType })
            ) { entry ->
                val attendanceId = entry.arguments?.getLong("attendanceId") ?: return@composable
                SurveyFormScreen(
                    attendanceId = attendanceId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.INDICATORS,
                arguments = listOf(navArgument("eventId") { type = NavType.LongType })
            ) { entry ->
                val eventId = entry.arguments?.getLong("eventId") ?: return@composable
                IndicatorsScreen(
                    eventId = eventId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.SCANNER,
                arguments = listOf(
                    navArgument("eventId") { type = NavType.LongType },
                    navArgument("useLocation") { type = NavType.BoolType }
                )
            ) { entry ->
                val eventId = entry.arguments?.getLong("eventId") ?: return@composable
                val useLocation = entry.arguments?.getBoolean("useLocation") ?: false
                ScannerScreen(
                    eventId = eventId,
                    useLocation = useLocation,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PROJECTS) {
                ProjectsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun navigateTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
