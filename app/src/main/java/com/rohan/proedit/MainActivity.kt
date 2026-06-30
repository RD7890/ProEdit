package com.rohan.proedit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rohan.proedit.ui.screens.EditorScreen
import com.rohan.proedit.ui.screens.HomeScreen
import com.rohan.proedit.ui.theme.NavyBg
import com.rohan.proedit.ui.theme.ProEditTheme
import com.rohan.proedit.viewmodel.EditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProEditTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = NavyBg,
                ) {
                    val navController = rememberNavController()
                    val viewModel: EditorViewModel = viewModel()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onImagePicked = { uri ->
                                    viewModel.loadImage(this@MainActivity, uri)
                                    navController.navigate("editor")
                                }
                            )
                        }
                        composable("editor") {
                            EditorScreen(
                                viewModel  = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
