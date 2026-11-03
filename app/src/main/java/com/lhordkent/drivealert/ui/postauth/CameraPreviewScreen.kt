package com.lhordkent.drivealert.ui.postauth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.frame.SharedBitmapFrame
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CameraPreviewScreen(
    vision: DriverVisionUiState,
    previewFrames: StateFlow<SharedBitmapFrame?>?,
    onStartVision: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onStartVision() }
    ScrollableScreen(title = "Preview Camera", onBack = onBack) {
        LiveCameraPreview(
            streamState = vision.streamState,
            previewFrames = previewFrames,
            showOverlays = false,
        )
    }
}
