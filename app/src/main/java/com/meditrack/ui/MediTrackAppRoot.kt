package com.meditrack.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.meditrack.R
import androidx.navigation.NavDestination
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
import com.meditrack.ui.vaccination.AddEditVaccinationScreen
import com.meditrack.ui.vaccination.VaccinationScreen

private object Routes {
    const val Dashboard = "dashboard"
    const val Inventory = "inventory"
    const val AddEdit = "add-edit"
    const val Detail = "detail"
    const val Settings = "settings"
    const val Vaccinations = "vaccinations"
    const val AddEditVaccination = "add-edit-vaccination"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun MediTrackAppRoot() {
    val navController = rememberNavController()
    val bottomDestinations = listOf(
        BottomDestination(Routes.Dashboard, stringResource(R.string.tab_today), Icons.Rounded.Today),
        BottomDestination(Routes.Inventory, stringResource(R.string.tab_medicines), Icons.Rounded.Inventory2),
        BottomDestination(Routes.Vaccinations, stringResource(R.string.tab_vaccines), Icons.Rounded.Vaccines),
        BottomDestination(Routes.Settings, stringResource(R.string.tab_settings), Icons.Rounded.Settings)
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route.orEmpty()
    val showChrome = currentRoute == Routes.Dashboard ||
        currentRoute == Routes.Inventory ||
        currentRoute == Routes.Vaccinations ||
        currentRoute == Routes.Settings
    val showAddButton = currentRoute == Routes.Dashboard || currentRoute == Routes.Inventory

    Scaffold(
        bottomBar = {
            if (showChrome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    bottomDestinations.forEach { destination ->
                        val selected = currentDestination.isSelected(destination.route)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(Routes.Dashboard) { saveState = true }
                                }
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = destination.label)
                            },
                            label = {
                                Text(
                                    destination.label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showAddButton) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Routes.AddEdit) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(stringResource(R.string.action_add_medicine), fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Dashboard,
            modifier = Modifier.padding(padding),
            enterTransition = {
                fadeIn(animationSpec = tween(180)) +
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(220)
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(120)) +
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(220)
                    )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(180)) +
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(220)
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(120)) +
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(220)
                    )
            }
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
            composable(Routes.Vaccinations) {
                VaccinationScreen(
                    onAddVaccination = { navController.navigate(Routes.AddEditVaccination) },
                    onEditVaccination = { navController.navigate("${Routes.AddEditVaccination}?vaccinationId=$it") }
                )
            }
            composable(
                route = "${Routes.AddEditVaccination}?vaccinationId={vaccinationId}",
                arguments = listOf(
                    navArgument("vaccinationId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val vaccinationId = entry.arguments?.getString("vaccinationId")?.toLongOrNull()
                AddEditVaccinationScreen(
                    vaccinationId = vaccinationId,
                    onSaved = {
                        navController.navigate(Routes.Vaccinations) {
                            popUpTo(Routes.Dashboard)
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable(Routes.Settings) {
                SettingsScreen()
            }
        }
    }
}

private fun NavDestination?.isSelected(route: String): Boolean {
    val currentRoute = this?.route.orEmpty()
    return when (route) {
        Routes.Inventory -> currentRoute == Routes.Inventory || currentRoute.startsWith(Routes.Detail)
        else -> currentRoute == route
    }
}
