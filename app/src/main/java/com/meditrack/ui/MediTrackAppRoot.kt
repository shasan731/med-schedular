package com.meditrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bottomRoutes.forEach { (route, label) ->
                        val selected = when (route) {
                            Routes.AddEdit -> currentRoute.startsWith(Routes.AddEdit)
                            Routes.Inventory -> currentRoute == Routes.Inventory ||
                                currentRoute.startsWith(Routes.Detail)
                            else -> currentRoute == route
                        }
                        BottomNavPill(
                            label = label,
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(Routes.Dashboard) { saveState = true }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
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

@Composable
private fun BottomNavPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(58.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(58.dp),
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text(label)
        }
    }
}
