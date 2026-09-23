package com.levidor.kehribarvideo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.levidor.kehribarvideo.ui.AdModeScreen
import com.levidor.kehribarvideo.ui.HomeScreen
import com.levidor.kehribarvideo.ui.ResultsScreen
import com.levidor.kehribarvideo.ui.SettingsScreen
import com.levidor.kehribarvideo.ui.theme.KehribarVideoTheme
import com.levidor.kehribarvideo.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KehribarVideoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KehribarNavHost(viewModel = mainViewModel)
                }
            }
        }
    }
}

@Composable
private fun KehribarNavHost(viewModel: MainViewModel) {
    val navController: NavHostController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate("settings") },
                onOpenAdMode = { navController.navigate("ad_mode") },
                onOpenResults = { navController.navigate("results") }
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("ad_mode") {
            AdModeScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("results") {
            ResultsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
