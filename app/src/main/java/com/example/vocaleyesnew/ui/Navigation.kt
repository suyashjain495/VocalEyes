package com.example.vocaleyesnew.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.vocaleyesnew.EnhancedHomeScreen
import com.example.vocaleyesnew.EnhancedMainActivity
import com.example.vocaleyesnew.objectdetection.ObjectDetectionActivity
import com.example.vocaleyesnew.textextraction.TextExtractionActivity
import com.example.vocaleyesnew.chat.ChatActivity
import com.example.vocaleyesnew.navigation.NavigationActivity
import com.example.vocaleyesnew.facenet_android.FaceActivity

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object ObjectDetection : Screen("object_detection")
    object Navigation : Screen("navigation")
    object FaceRecognition : Screen("face_recognition")
    object TextExtraction : Screen("text_extraction")
    object Chat : Screen("chat")
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            val localContext = LocalContext.current
            EnhancedHomeScreen(
                activity = localContext as EnhancedMainActivity,
                onNavigateToFeature = { featureClass ->
                    localContext.startActivity(Intent(localContext, featureClass))
                }
            )
        }
        composable(Screen.ObjectDetection.route) {
            scope.launch {
                context.startActivity(Intent(context, ObjectDetectionActivity::class.java))
            }
        }
        composable(Screen.Navigation.route) {
            scope.launch {
                context.startActivity(Intent(context, NavigationActivity::class.java))
            }
        }
        composable(Screen.FaceRecognition.route) {
            scope.launch {
                context.startActivity(Intent(context, FaceActivity::class.java))
            }
        }
        composable(Screen.TextExtraction.route) {
            scope.launch {
                context.startActivity(Intent(context, TextExtractionActivity::class.java))
            }
        }
        composable(Screen.Chat.route) {
            scope.launch {
                context.startActivity(Intent(context, ChatActivity::class.java))
            }
        }
    }
}

