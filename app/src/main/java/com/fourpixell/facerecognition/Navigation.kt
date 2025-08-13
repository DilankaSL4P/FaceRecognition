package com.fourpixell.facerecognition

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fourpixell.facerecognition.homesection.HomeScreen
import com.fourpixell.facerecognition.registerfacesection.RegisterFaceScreen


object AppRoutes {
    const val HOME = "home"
    const val SCAN_FACE = "scan_face/{scanType}"

    fun scanFaceRoute(scanType: String) = "scan_face/$scanType"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // NavHost is the container for all your screens
    NavHost(navController = navController, startDestination = AppRoutes.HOME) {

        // Home Screen Route
        composable(AppRoutes.HOME) {
            HomeScreen(
                onNavigateToRegister = {
                    // Navigate to the scan screen with "register" type
                    navController.navigate(AppRoutes.scanFaceRoute("register"))
                },
                onNavigateToVerify = {
                    // Navigate to the scan screen with "verify" type
                    navController.navigate(AppRoutes.scanFaceRoute("verify"))
                }
            )
        }

        // Face Scan Screen Route
        composable(
            route = AppRoutes.SCAN_FACE,
            arguments = listOf(navArgument("scanType") { type = NavType.StringType })
        ) { backStackEntry ->
            // Extract the scanType argument from the route
            val scanType = backStackEntry.arguments?.getString("scanType") ?: "unknown"
            RegisterFaceScreen(scanType = scanType, navController = navController)
        }
    }
}