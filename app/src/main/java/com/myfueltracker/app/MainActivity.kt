package com.myfueltracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.myfueltracker.app.ui.*
// Compose State & Lifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.myfueltracker.app.BuildConfig

// Compose UI Components
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

// Your New Update System Classes
import com.myfueltracker.app.utils.UpdateManager
import com.myfueltracker.app.data.remote.GitHubRelease


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val viewModel: FuelViewModel = viewModel()

            val isFirstRun by viewModel.isFirstRun.collectAsState(initial = true)
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            val context = androidx.compose.ui.platform.LocalContext.current
            val updateManager = remember { UpdateManager(context) }
            var updateInfo by remember { mutableStateOf<GitHubRelease?>(null) }

            // Check for updates on launch
            LaunchedEffect(Unit) {
                // Dynamically reads your current tracking layout version from build.gradle (e.g., "2.0")
                val currentVersion = BuildConfig.VERSION_NAME
                val latest = updateManager.checkForUpdates(currentVersion)

                if (latest != null) {
                    // Double-check using semantic version comparison logic to handle character prefix patterns (v2.0 vs 2.0)
                    if (isUpdateAvailable(currentVersion = currentVersion, latestVersion = latest.tag_name)) {
                        updateInfo = latest
                    }
                }
            }

            // Show the dialog if an update is found
            if (updateInfo != null) {
                AlertDialog(
                    onDismissRequest = { updateInfo = null },
                    title = { Text("Update Available") },
                    text = { Text("A new version (${updateInfo?.tag_name}) is ready. Would you like to download it?") },
                    confirmButton = {
                        Button(onClick = {
                            updateInfo?.assets?.firstOrNull()?.let {
                                updateManager.downloadAndInstall(it.browser_download_url)
                            }
                            updateInfo = null
                        }) { Text("Download") }
                    },
                    dismissButton = {
                        TextButton(onClick = { updateInfo = null }) { Text("Later") }
                    }
                )
            }

            // Routes where the bottom navigation bar should be hidden
            val hideBottomBarRoutes = listOf(
                "welcome",
                "offline_reg",
                Screen.EditProfile.route,
                Screen.AddVehicle.route,
                Screen.EditVehicle.route + "/{vehicleId}",
                Screen.AddFuel.route + "/{vehicleId}",
                Screen.AddService.route + "/{vehicleId}"
            )

            Scaffold(
                bottomBar = {
                    // Show bottom bar only if not on a 'hide' route and not in the first-run flow
                    val shouldShowBottomBar = currentRoute != null &&
                            currentRoute !in hideBottomBarRoutes &&
                            !isFirstRun

                    if (shouldShowBottomBar) {
                        BottomNavigationBar(navController)
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = if (isFirstRun) "welcome" else Screen.Dashboard.route,
                    modifier = Modifier.padding(innerPadding)
                ) {

                    composable("welcome") {
                        WelcomeScreen(
                            viewModel = viewModel,
                            onGetStarted = {
                                // Triggered after successful Google Login
                                viewModel.setFirstRunCompleted()
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo("welcome") { inclusive = true }
                                }
                            },
                            onOfflineClick = {
                                navController.navigate("offline_reg")
                            }
                        )
                    }

                    composable("offline_reg") {
                        OfflineRegistrationScreen(
                            viewModel = viewModel,
                            onRegistrationComplete = {
                                viewModel.setFirstRunCompleted()
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo("welcome") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Dashboard.route) {
                        DashboardScreen(viewModel = viewModel, navController = navController)
                    }

                    composable(Screen.MonthlySummary.route) {
                        MonthlySummaryScreen(viewModel = viewModel)
                    }

                    composable(Screen.History.route) {
                        HistoryScreen(viewModel = viewModel, navController = navController)
                    }

                    composable(Screen.UserProfile.route) {
                        UserProfileScreen(
                            viewModel = viewModel,
                            onAddVehicleClick = { navController.navigate(Screen.AddVehicle.route) },
                            onEditVehicleClick = { vId ->
                                navController.navigate(Screen.EditVehicle.route + "/$vId")
                            },
                            onEditProfileClick = { navController.navigate(Screen.EditProfile.route) },
                            onLogout = {
                                navController.navigate("welcome") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.EditProfile.route) {
                        EditProfileScreen(
                            viewModel = viewModel,
                            onBackClick = { navController.popBackStack() },
                            onSaveComplete = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onLogout = {
                                navController.navigate("welcome") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.AddVehicle.route) {
                        AddVehicleScreen(
                            viewModel = viewModel,
                            onSaveComplete = { navController.popBackStack() },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Screen.EditVehicle.route + "/{vehicleId}",
                        arguments = listOf(navArgument("vehicleId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val vId = backStackEntry.arguments?.getInt("vehicleId") ?: 0
                        AddVehicleScreen(
                            viewModel = viewModel,
                            vehicleId = vId,
                            onSaveComplete = { navController.popBackStack() },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Screen.AddFuel.route + "/{vehicleId}",
                        arguments = listOf(navArgument("vehicleId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        // 1. You defined the variable as 'vId' here...
                        val vIdFromNav = backStackEntry.arguments?.getInt("vehicleId") ?: 0

                        AddFuelScreen(
                            // 2. So you must use 'vIdFromNav' here!
                            vehicleId = vIdFromNav,
                            viewModel = viewModel,
                            onBackClick = { navController.popBackStack() },
                            onSaveComplete = { navController.popBackStack() },
                            onSaveClick = { vehicleId, id, odo, amt, price, full, notes, date,
                                            sName, loc, phone, types, hour, hosp, wash, wait, road ->
                                if (id == -1) {
                                    viewModel.addFuelEntry(odo, amt, price, full, notes, date,
                                        sName, loc, phone, types, hour, hosp, wash, wait, road)
                                } else {
                                    viewModel.updateFuelEntry(id, vehicleId, odo, amt, price, full, notes, date,
                                        sName, loc, phone, types, hour, hosp, wash, wait, road)
                                }
                            }
                        )
                    }

                    composable(
                        route = Screen.AddService.route + "/{vehicleId}",
                        arguments = listOf(navArgument("vehicleId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val vId = backStackEntry.arguments?.getInt("vehicleId") ?: 0
                        AddServiceScreen(
                            vehicleId = vId,
                            viewModel = viewModel,
                            onSaveComplete = { navController.popBackStack() },
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    /**
     * Semantic Version Comparison Engine
     * Strips "v" prefixes, trims white space, splits strings into integer arrays,
     * and performs an element-by-element numerical verification.
     * Prevents the infinite prompt bug by identifying "2.0" matching "v2.0" identically.
     */
    private fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        val cleanCurrent = currentVersion.lowercase().removePrefix("v").trim()
        val cleanLatest = latestVersion.lowercase().removePrefix("v").trim()

        if (cleanCurrent == cleanLatest) return false

        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val latestPart = latestParts.getOrElse(i) { 0 }

            if (latestPart > currentPart) return true   // GitHub release version is newer
            if (currentPart > latestPart) return false  // App local version is newer
        }
        return false
    }
}
