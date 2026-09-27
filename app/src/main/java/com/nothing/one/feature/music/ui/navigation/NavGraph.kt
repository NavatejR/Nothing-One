package com.nothing.one.feature.music.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nothing.one.feature.music.ui.PlayerViewModel
import com.nothing.one.feature.music.ui.animation.NothingOSEnter
import com.nothing.one.feature.music.ui.animation.NothingOSExit
import com.nothing.one.feature.music.ui.animation.NothingOSPopEnter
import com.nothing.one.feature.music.ui.animation.NothingOSPopExit
import com.nothing.one.feature.music.ui.screen.LibraryScreen
import com.nothing.one.feature.music.ui.screen.NowPlayingScreen
import com.nothing.one.feature.music.ui.screen.PlaylistDetailScreen
import com.nothing.one.feature.music.ui.screen.SearchScreen
import com.nothing.one.feature.music.ui.screen.SettingsScreen
import com.nothing.one.feature.music.ui.screen.queue.QueueScreen

object MusicRoutes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "nowplaying"
    const val QUEUE = "queue?startIndex={startIndex}"
    const val EQ = "equalizer"
    const val FOLDER_PICKER = "folderpicker"
    const val ALBUM_DETAIL = "album/{albumName}/{albumArtist}"
    const val PLAYLIST = "playlist/{playlistId}"

    fun albumDetail(albumName: String, albumArtist: String) =
        "album/${android.net.Uri.encode(albumName)}/${android.net.Uri.encode(albumArtist)}"

    fun playlist(playlistId: Long) = "playlist/$playlistId"
}

@Composable
fun NothingMusicNavGraph(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = MusicRoutes.LIBRARY,
        enterTransition = NothingOSEnter,
        exitTransition = NothingOSExit,
        popEnterTransition = NothingOSPopEnter,
        popExitTransition = NothingOSPopExit,
    ) {
        composable(MusicRoutes.LIBRARY) {
            LibraryScreen(
                playerViewModel = playerViewModel,
                onNavigateToNowPlaying = { navController.navigate(MusicRoutes.NOW_PLAYING) },
                onNavigateToPlaylist = { id -> navController.navigate(MusicRoutes.playlist(id)) },
                onNavigateToQueue = { navController.navigate(MusicRoutes.QUEUE) },
                onNavigateToFolderPicker = { navController.navigate(MusicRoutes.FOLDER_PICKER) },
            )
        }
        composable(MusicRoutes.SEARCH) {
            SearchScreen(
                playerViewModel = playerViewModel,
                onNavigateToNowPlaying = { navController.navigate(MusicRoutes.NOW_PLAYING) },
            )
        }
        composable(MusicRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateToEqualizer = { navController.navigate(MusicRoutes.EQ) },
                onNavigateToFolderPicker = { navController.navigate(MusicRoutes.FOLDER_PICKER) },
            )
        }
        composable(MusicRoutes.NOW_PLAYING) {
            NowPlayingScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToQueue = { navController.navigate(MusicRoutes.QUEUE) },
            )
        }
        composable(
            route = MusicRoutes.QUEUE,
            arguments = listOf(
                navArgument("startIndex") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
        ) {
            QueueScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(MusicRoutes.EQ) {
            com.nothing.one.feature.music.ui.screen.equalizer.EqualizerScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(MusicRoutes.FOLDER_PICKER) {
            com.nothing.one.feature.music.ui.screen.folderpicker.FolderPickerScreen(
                onDone = { navController.popBackStack() },
            )
        }
        composable(
            route = MusicRoutes.ALBUM_DETAIL,
            arguments = listOf(
                navArgument("albumName") { type = NavType.StringType },
                navArgument("albumArtist") { type = NavType.StringType },
            ),
        ) {
            com.nothing.one.feature.music.ui.screen.AlbumDetailScreen(
                albumName = android.net.Uri.decode(it.arguments?.getString("albumName") ?: ""),
                albumArtist = android.net.Uri.decode(it.arguments?.getString("albumArtist") ?: ""),
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = MusicRoutes.PLAYLIST,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType },
            ),
        ) {
            val playlistId = it.arguments?.getLong("playlistId") ?: 0L
            PlaylistDetailScreen(
                playlistId = playlistId,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToNowPlaying = { navController.navigate(MusicRoutes.NOW_PLAYING) },
            )
        }
    }
}