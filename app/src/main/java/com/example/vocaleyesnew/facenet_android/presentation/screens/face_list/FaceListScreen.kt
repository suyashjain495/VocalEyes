package com.example.vocaleyesnew.facenet_android.presentation.screens.face_list

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vocaleyesnew.VoiceRecognitionManager
import com.example.vocaleyesnew.facenet_android.data.PersonRecord
import com.example.vocaleyesnew.facenet_android.presentation.components.AppAlertDialog
import com.example.vocaleyesnew.facenet_android.presentation.components.createAlertDialog
import com.example.vocaleyesnew.facenet_android.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceListScreen(onNavigateBack: (() -> Unit), onAddFaceClick: (() -> Unit)) {
    // Get the context for voice recognition
    val context = LocalContext.current
    
    // Create or remember the voice recognition manager for permissions
    val voiceRecognitionManager = remember { VoiceRecognitionManager(context) }
    
    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = "Face List", style = MaterialTheme.typography.headlineSmall)
                    },
                    navigationIcon = {
                        // Back button with direct single tap navigation
                        IconButton(
                            onClick = { onNavigateBack() },
                            modifier = Modifier
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = "Navigate Back"
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                // FAB with direct single tap navigation
                FloatingActionButton(
                    onClick = { onAddFaceClick() }
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add a new face")
                }
            }
        ) { innerPadding ->
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val viewModel: FaceListScreenViewModel = koinViewModel()
                ScreenUI(viewModel)
                AppAlertDialog()
            }
        }
    }
}

@Composable
private fun ScreenUI(viewModel: FaceListScreenViewModel) {
    val faces by viewModel.personFlow.collectAsState(emptyList())
    
    LazyColumn { 
        items(faces) { person -> 
            FaceListItem(
                personRecord = person,
                onRemove = { viewModel.removeFace(person.personID) }
            ) 
        } 
    }
}

@Composable
private fun FaceListItem(
    personRecord: PersonRecord,
    onRemove: () -> Unit
) {
    // Use single tap with direct click handling
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = personRecord.personName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = DateUtils.getRelativeTimeSpanString(personRecord.addTime).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray
            )
        }
        Icon(
            modifier = Modifier.clickable {
                // Show confirmation dialog on single tap
                createAlertDialog(
                    dialogTitle = "Remove person",
                    dialogText = "Are you sure to remove this person from the database? The face for this person will not be detected in realtime.",
                    dialogPositiveButtonText = "Remove",
                    onPositiveButtonClick = onRemove,
                    dialogNegativeButtonText = "Cancel",
                    onNegativeButtonClick = {}
                )
            },
            imageVector = Icons.Default.Clear,
            contentDescription = "Remove face"
        )
        Spacer(modifier = Modifier.width(2.dp))
    }
}
