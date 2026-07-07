package com.meditrack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meditrack.ui.addedit.AddEditMedicationScreen
import com.meditrack.ui.dashboard.DashboardScreen
import com.meditrack.ui.detail.MedicationDetailScreen
import com.meditrack.ui.inventory.InventoryScreen
import com.meditrack.ui.settings.SettingsScreen

private object Routes {
    const val Dashboard = "dashboard"
    const val Inventory = "inventory"
    const val AddEdit = "add-edit"
    const val Detail = "detail"
    const val Settings = "settings"
}

@Composable
fun MediTrackAppRoot() {
    val navController = rememberNavController()
    val bottomRoutes = listOf(
        Routes.Dashboard to "Today",
        Routes.Inventory to "Inventory",
        Routes.AddEdit to "Add Med",
        Routes.Settings to "Settings"
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route.orEmpty()

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomRoutes.forEach { (route, label) ->
                    val selected = when (route) {
                        Routes.AddEdit -> currentRoute.startsWith(Routes.AddEdit)
                        Routes.Detail -> currentRoute.startsWith(Routes.Detail)
                        else -> currentRoute == route
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(Routes.Dashboard) { saveState = true }
                            }
                        },
                        icon = {
                            Text(
                                text = if (selected) "[x]" else "[ ]",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Dashboard,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.Dashboard) {
                DashboardScreen(
                    onAddMedication = { navController.navigate(Routes.AddEdit) },
                    onMedicationClick = { navController.navigate("${Routes.Detail}/$it") }
                )
            }
            composable(Routes.Inventory) {
                InventoryScreen(
                    onAddMedication = { navController.navigate(Routes.AddEdit) },
                    onEditMedication = { navController.navigate("${Routes.AddEdit}?medicationId=$it") },
                    onMedicationClick = { navController.navigate("${Routes.Detail}/$it") }
                )
            }
            composable(
                route = "${Routes.AddEdit}?medicationId={medicationId}",
                arguments = listOf(
                    navArgument("medicationId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val medicationId = entry.arguments?.getString("medicationId")?.toLongOrNull()
                AddEditMedicationScreen(
                    medicationId = medicationId,
                    onSaved = {
                        navController.navigate(Routes.Inventory) {
                            popUpTo(Routes.Dashboard)
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable("${Routes.Detail}/{medicationId}") { entry ->
                val medicationId = entry.arguments?.getString("medicationId")?.toLongOrNull() ?: 0L
                MedicationDetailScreen(
                    medicationId = medicationId,
                    onEdit = { navController.navigate("${Routes.AddEdit}?medicationId=$it") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.Settings) {
                SettingsScreen()
            }
        }
    }
}
