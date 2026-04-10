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
import com.stopforfuel.app.ui.customer.CustomerListScreen
import com.stopforfuel.app.ui.customer.CustomerDetailScreen
import com.stopforfuel.app.ui.employee.EmployeeManageScreen
import com.stopforfuel.app.ui.dashboard.DashboardScreen
import com.stopforfuel.app.ui.product.ProductManageScreen
import com.stopforfuel.app.ui.fastcash.FastCashInvoiceScreen
import com.stopforfuel.app.ui.explorer.InvoiceBillExplorerScreen
import com.stopforfuel.app.ui.explorer.StatementExplorerScreen
import com.stopforfuel.app.ui.stocktransfer.StockTransferScreen
import com.stopforfuel.app.ui.attendance.AttendanceScreen
import com.stopforfuel.app.ui.invoiceupload.InvoiceUploadScreen

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
                onNavigateToCustomers = {
                    navController.navigate(Routes.CustomerList.route)
                },
                onNavigateToEmployees = {
                    navController.navigate(Routes.EmployeeManage.route)
                },
                onNavigateToDashboard = {
                    navController.navigate(Routes.Dashboard.route)
                },
                onNavigateToProducts = {
                    navController.navigate(Routes.ProductManage.route)
                },
                onNavigateToFastCashInvoice = {
                    navController.navigate(Routes.FastCashInvoice.route)
                },
                onNavigateToInvoiceBillExplorer = {
                    navController.navigate(Routes.InvoiceBillExplorer.route)
                },
                onNavigateToStatementExplorer = {
                    navController.navigate(Routes.StatementExplorer.route)
                },
                onNavigateToStockTransfer = {
                    navController.navigate(Routes.StockTransfer.route)
                },
                onNavigateToAttendance = {
                    navController.navigate(Routes.Attendance.route)
                },
                onNavigateToInvoiceUpload = {
                    navController.navigate(Routes.InvoiceUpload.route)
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

        composable(Routes.CustomerList.route) {
            CustomerListScreen(
                onBack = { navController.popBackStack() },
                onCustomerSelected = { customerId ->
                    navController.navigate(Routes.CustomerDetail.withId(customerId))
                }
            )
        }

        composable(
            route = Routes.CustomerDetail.route,
            arguments = listOf(navArgument("customerId") { type = NavType.LongType })
        ) {
            CustomerDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.EmployeeManage.route) {
            EmployeeManageScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.Dashboard.route) {
            DashboardScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ProductManage.route) {
            ProductManageScreen(onBack = { navController.popBackStack() })
        }

        // ── New screens ──

        composable(Routes.FastCashInvoice.route) {
            FastCashInvoiceScreen(
                onBack = { navController.popBackStack() },
                onInvoiceCreated = { navController.popBackStack() }
            )
        }

        composable(Routes.InvoiceBillExplorer.route) {
            InvoiceBillExplorerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.StatementExplorer.route) {
            StatementExplorerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.StockTransfer.route) {
            StockTransferScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.Attendance.route) {
            AttendanceScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.InvoiceUpload.route) {
            InvoiceUploadScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
