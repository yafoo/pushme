package com.android.pushme.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.android.pushme.LocalNavController
import com.android.pushme.screen.ApiScreen
import com.android.pushme.screen.DashboardScreen
import com.android.pushme.screen.FileScreen
import com.android.pushme.screen.ChatScreen
import com.android.pushme.screen.HomeScreen
import com.android.pushme.screen.HostScreen
import com.android.pushme.screen.MessageScreen
import com.android.pushme.screen.NoteEditScreen
import com.android.pushme.screen.NotifyAppFilterScreen
import com.android.pushme.screen.NotifyFilterScreen
import com.android.pushme.screen.PluginEnvScreen
import com.android.pushme.screen.PluginPmfScreen
import com.android.pushme.screen.PluginSaveScreen
import com.android.pushme.screen.PluginScreen
import com.android.pushme.screen.PluginStoreScreen
import com.android.pushme.screen.RepostScreen
import com.android.pushme.screen.SendScreen
import com.android.pushme.screen.TaskEditScreen
import com.android.pushme.screen.TaskScreen
import com.android.pushme.screen.UserScreen
import com.android.pushme.screen.WebScreen
import java.net.URLDecoder

fun enterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth }, // 从右侧滑入
        animationSpec = tween(300) // 动画时长 300ms
    )
}
fun exitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth }, // 向左滑出
        animationSpec = tween(300) // 动画时长 300ms
    )
}
fun popEnterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth }, // 从左侧滑入
        animationSpec = tween(300) // 动画时长 300ms
    )
}
fun popExitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth }, // 向右侧滑出
        animationSpec = tween(300) // 动画时长 300ms
    )
}

@Composable
fun MeAppNavHost(
    modifier: Modifier = Modifier,
    startDestination: String = "home"
) {
    val navController = LocalNavController.current
    NavHost(
        modifier = modifier.background(MaterialTheme.colorScheme.primary).statusBarsPadding(),
        navController = navController,
        startDestination = startDestination,
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() }
    ) {
        composable("home") {
            HomeScreen()
        }
        composable("user") {
            UserScreen()
        }
        composable("chat") {
            ChatScreen()
        }
        composable(
            "message/{messageId}",
            listOf(navArgument("messageId") { type = NavType.IntType })
        ) { backStackEntry ->
            MessageScreen(
                messageId = backStackEntry.arguments?.getInt("messageId")?: 0
            )
        }
        composable("send") {
            SendScreen()
        }
        composable("dashboard") {
            DashboardScreen()
        }
        composable("plugin") {
            PluginScreen()
        }
        composable("plugin_store") {
            PluginStoreScreen()
        }
        composable(
            "plugin/{pluginId}",
            listOf(navArgument("pluginId") { type = NavType.IntType })
        ) { backStackEntry ->
            PluginSaveScreen(pluginId = backStackEntry.arguments?.getInt("pluginId") ?: 0)
        }
        composable("plugin_env") {
            PluginEnvScreen()
        }
        composable("plugin_pmf") {
            PluginPmfScreen()
        }
        composable("host") {
            HostScreen()
        }
        composable("api") {
            ApiScreen()
        }
        composable("repost") {
            RepostScreen()
        }
        composable("notify_filter") {
            NotifyFilterScreen()
        }
        composable("notify_app_filter") {
            NotifyAppFilterScreen()
        }
        composable("task") {
            TaskScreen()
        }
        composable(
            "task_edit/{taskId}",
            listOf(navArgument("taskId") { type = NavType.IntType })
        ) { backStackEntry ->
            TaskEditScreen(taskId = backStackEntry.arguments?.getInt("taskId") ?: 0)
        }
        composable("file") {
            FileScreen()
        }
        composable(
            "note_edit/{noteId}",
            listOf(navArgument("noteId") { type = NavType.IntType })
        ) { backStackEntry ->
            NoteEditScreen(noteId = backStackEntry.arguments?.getInt("noteId") ?: 0)
        }
        composable("note_add") {
            NoteEditScreen()
        }
        composable("web/{url}",
            listOf(navArgument("url") {type = NavType.StringType})
        ) { backStackEntry ->
            val url = URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", "UTF-8")
            WebScreen(url = url)
        }
    }
}