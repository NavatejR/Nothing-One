package com.nothing.one

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.nothing.one.feature.focus.ui.FocusScreen
import com.nothing.one.feature.music.ui.PlayerViewModel
import com.nothing.one.feature.music.ui.component.MiniPlayer
import com.nothing.one.feature.music.ui.component.PermissionRationaleScreen
import com.nothing.one.feature.music.ui.navigation.MusicRoutes
import com.nothing.one.feature.music.ui.navigation.NothingMusicNavGraph
import com.nothing.one.feature.music.util.rememberPermissionState
import com.nothing.one.ui.animation.NothingOSEnter
import com.nothing.one.ui.animation.NothingOSExit
import com.nothing.one.ui.animation.NothingOSPopEnter
import com.nothing.one.ui.animation.NothingOSPopExit
import com.nothing.one.ui.navigation.NothingOneBottomNavBar
import com.nothing.one.ui.screen.chat.ChatScreen
import com.nothing.one.ui.screen.editor.NoteEditorScreen
import com.nothing.one.ui.screen.home.HomeScreen
import com.nothing.one.ui.screen.journal.JournalScreen
import com.nothing.one.ui.screen.onboarding.OnboardingScreen
import com.nothing.one.ui.screen.settings.SettingsScreen
import com.nothing.one.ui.theme.NothingJournalTheme
import dagger.hilt.android.AndroidEntryPoint

object Routes {
    const val HOME = "home"
    const val MUSIC = "music"
    const val NOTES = "notes"
    const val JOURNAL = "journal"
    const val ASSISTANT = "assistant"
    const val FOCUS = "focus"
    const val CALENDAR = "calendar"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val EDITOR = "editor?noteId={noteId}&templateId={templateId}"
    const val ONBOARDING = "onboarding"

    fun editor(noteId: Long? = null, templateId: String? = null): String {
        val params = mutableListOf<String>()
        if (noteId != null) params += "noteId=$noteId"
        if (templateId != null) params += "templateId=$templateId"
        return if (params.isEmpty()) "editor" else "editor?" + params.joinToString("&")
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appSettings by viewModel.settings.collectAsState()
            NothingJournalTheme(settings = appSettings) {
                NothingOneApp(viewModel)
            }
        }
    }
}

@Composable
private fun NothingOneApp(viewModel: AssistantViewModel) {
    val navController = rememberNavController()
    val startRoute: String? by viewModel.startRoute.collectAsState()

    if (startRoute == null) return // settings still loading

    val playerViewModel: PlayerViewModel = viewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()

    val currentTrack by playerViewModel.currentTrack.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val position by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()

    // The player's audio permission only matters inside the music tree.
    val musicPermission = rememberPermissionState()
    val inMusicTab = currentRoute == Routes.MUSIC
    LaunchedEffect(inMusicTab, musicPermission.isGranted) {
        if (inMusicTab && !musicPermission.isGranted) musicPermission.launchRequest()
    }

    val isTopLevel = currentRoute in setOf(
        Routes.HOME, Routes.MUSIC, Routes.NOTES, Routes.JOURNAL,
        Routes.ASSISTANT, Routes.FOCUS, Routes.CALENDAR,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = startRoute ?: Routes.HOME,
                enterTransition = NothingOSEnter,
                exitTransition = NothingOSExit,
                popEnterTransition = NothingOSPopEnter,
                popExitTransition = NothingOSPopExit,
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onDone = {
                            viewModel.completeOnboarding()
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenEditor = { navController.navigate(Routes.editor(it)) },
                        onOpenEditorWithTemplate = { templateId ->
                            navController.navigate(Routes.editor(templateId = templateId))
                        },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigate = { route ->
                            if (route != Routes.HOME) {
                                navController.navigate(route) { launchSingleTop = true }
                            }
                        },
                    )
                }
                composable(Routes.MUSIC) {
                    // Own controller: the music graph's routes never touch the
                    // root graph, and back handling nests correctly (inner
                    // pops first, then the root tab).
                    val musicNavController = rememberNavController()
                    if (musicPermission.isGranted) {
                        NothingMusicNavGraph(musicNavController, playerViewModel)
                    } else {
                        PermissionRationaleScreen(onRequestPermission = { musicPermission.launchRequest() })
                    }
                }
                composable(Routes.NOTES) {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenEditor = { navController.navigate(Routes.editor(it)) },
                        onOpenEditorWithTemplate = { templateId ->
                            navController.navigate(Routes.editor(templateId = templateId))
                        },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigate = { route ->
                            if (route != Routes.NOTES) {
                                navController.navigate(route) { launchSingleTop = true }
                            }
                        },
                    )
                }
                composable(Routes.JOURNAL) {
                    JournalScreen(
                        viewModel = viewModel,
                        onNavigate = { route ->
                            if (route != Routes.JOURNAL) {
                                navController.navigate(route) { launchSingleTop = true }
                            }
                        },
                    )
                }
                composable(Routes.ASSISTANT) {
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    ChatScreen(viewModel = chatViewModel)
                }
                composable(Routes.FOCUS) { FocusScreen() }
                composable(Routes.CALENDAR) {
                    com.nothing.one.feature.calendar.ui.CalendarScreen()
                }
                composable(Routes.INSIGHTS) {
                    com.nothing.one.ui.screen.insights.InsightsScreen(
                        onNavigate = { route ->
                            if (route != Routes.INSIGHTS) {
                                navController.navigate(route) { launchSingleTop = true }
                            }
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.EDITOR,
                    arguments = listOf(
                        navArgument("noteId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                        navArgument("templateId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val noteId = entry.arguments?.getLong("noteId") ?: -1L
                    val templateId = entry.arguments?.getString("templateId").orEmpty()
                    NoteEditorScreen(
                        noteId = if (noteId == -1L) null else noteId,
                        templateId = templateId.ifBlank { null },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        if (isTopLevel) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                // The music mini player follows the user across tabs.
                MiniPlayer(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    positionMs = position,
                    durationMs = duration,
                    onPlayPause = playerViewModel::playPause,
                    onNext = playerViewModel::next,
                    onClick = { navController.navigate(Routes.MUSIC) },
                    onSeek = {},
                )
                NothingOneBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}
