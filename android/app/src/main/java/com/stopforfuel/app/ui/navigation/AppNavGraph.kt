package com.stopforfuel.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stopforfuel.app.data.local.TokenStore
import com.stopforfuel.app.ui.home.HomeScreen
import com.stopforfuel.app.ui.invoice.InvoiceScreen
import com.stopforfuel.app.ui.login.LoginScreen
import com.stopforfuel.app.ui.history.ShiftInvoicesScreen
import com.stopforfuel.app.ui.pumpsession.StartPumpSessionScreen
import com.stopforfuel.app.ui.pumpsession.EndPumpSessionScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Login.route
    ) {
        composable(Routes.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Home.route) {
            HomeScreen(
                onNavigateToInvoice = {
                    navController.navigate(Routes.CreateInvoice.route)
                },
                onNavigateToShiftInvoices = {
                    navController.navigate(Routes.ShiftInvoices.route)
                },
                onNavigateToStartSession = {
                    navController.navigate(Routes.StartPumpSession.route)
                },
                onNavigateToEndSession = { sessionId ->
                    navController.navigate(Routes.EndPumpSession.withId(sessionId))
                },
                onLogout = {
                    navController.navigate(Routes.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CreateInvoice.route) {
            InvoiceScreen(
                onBack = { navController.popBackStack() },
                onInvoiceCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.ShiftInvoices.route) {
            ShiftInvoicesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.StartPumpSession.route) {
            StartPumpSessionScreen(
                onBack = { navController.popBackStack() },
                onSessionStarted = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EndPumpSession.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            EndPumpSessionScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onSessionClosed = { navController.popBackStack() }
            )
        }
    }
}
