package com.zbxapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zbxapp.di.AppContainer
import com.zbxapp.ui.screens.graph.GraphScreen
import com.zbxapp.ui.screens.login.LoginScreen
import com.zbxapp.ui.screens.problemdetail.ProblemDetailScreen
import com.zbxapp.ui.screens.problems.ProblemsScreen
import com.zbxapp.ui.screens.settings.SettingsScreen

object Routes {
    const val LOGIN = "login"
    const val PROBLEMS = "problems"
    const val PROBLEM_DETAIL = "problem/{eventId}"
    const val GRAPH = "graph/{itemId}"
    const val SETTINGS = "settings"

    fun problemDetail(eventId: String) = "problem/$eventId"
    fun graph(itemId: String) = "graph/$itemId"
}

@Composable
fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val auth by container.storage.state.collectAsStateWithLifecycle()

    val startRoute = if (auth.isAuthenticated) Routes.PROBLEMS else Routes.LOGIN

    NavHost(navController = navController, startDestination = startRoute) {
        composable(Routes.LOGIN) {
            LoginScreen(
                container = container,
                onLoggedIn = {
                    navController.navigate(Routes.PROBLEMS) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.PROBLEMS) {
            ProblemsScreen(
                container = container,
                onOpenProblem = { eventId -> navController.navigate(Routes.problemDetail(eventId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.PROBLEM_DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
        ) { entry ->
            val eventId = entry.arguments?.getString("eventId").orEmpty()
            ProblemDetailScreen(
                container = container,
                eventId = eventId,
                onBack = { navController.popBackStack() },
                onOpenItemGraph = { itemId -> navController.navigate(Routes.graph(itemId)) },
            )
        }
        composable(
            route = Routes.GRAPH,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { entry ->
            val itemId = entry.arguments?.getString("itemId").orEmpty()
            GraphScreen(
                container = container,
                itemId = itemId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0)
                    }
                },
            )
        }
    }
}
