package com.lhordkent.drivealert.detection

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.lhordkent.drivealert.DriveAlertApplication
import com.lhordkent.drivealert.detection.face.DriverRegion
import com.lhordkent.drivealert.detection.face.FaceAttribNetClassifier
import com.lhordkent.drivealert.detection.face.FaceCropCalculator
import com.lhordkent.drivealert.detection.face.MediaPipeFaceLandmarkerProcessor
import com.lhordkent.drivealert.detection.model.FaceObservation
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import com.lhordkent.drivealert.device.authorizeDevice
import com.lhordkent.drivealert.device.AndroidDriveAlertEndpointResolver
import com.lhordkent.drivealert.device.requireDeviceResponse
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in live measurement, never part of ordinary test runs. No Activity,
 * account creation, Room writes or speaker commands. Images are saved only
 * when exportModelInputs=true is explicitly supplied. */
class FaceAttributeCropDiagnosticTest {
    @Test fun compareFaceCropsOnCurrentCameraFrame() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveCameraDiagnostic") == "true")
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DriveAlertApplication
        val uid = requireNotNull(FirebaseAuth.getInstance().currentUser?.uid) { "Existing signed-in account required." }
        val savedDevice = requireNotNull(application.container.provisionedDeviceRepository.active(uid)) { "Existing camera session required." }
        val device = requireNotNull(AndroidDriveAlertEndpointResolver(application).resolve(savedDevice)) {
            "Saved camera is unreachable and named-camera discovery did not find it."
        }
        val exportDirectory = if (InstrumentationRegistry.getArguments().getString("exportModelInputs") == "true") {
            File(application.filesDir, "crop-diagnostic").apply { mkdirs() }
        } else null
        val sampleCount = InstrumentationRegistry.getArguments().getString("diagnosticSamples")?.toIntOrNull() ?: 5
        require(sampleCount in 1..20) { "Diagnostic must be bounded to 1..20 samples." }
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MediaPipeFaceLandmarkerProcessor.MODEL_ASSET_PATH).build())
            .setRunningMode(RunningMode.IMAGE).setNumFaces(4)
            .setOutputFaceBlendshapes(true)
            .setMinFaceDetectionConfidence(MediaPipeFaceLandmarkerProcessor.MIN_FACE_CONFIDENCE)
            .setMinFacePresenceConfidence(MediaPipeFaceLandmarkerProcessor.MIN_FACE_CONFIDENCE)
            .build()
        FaceLandmarker.createFromOptions(application, options).use { model ->
            FaceAttribNetClassifier(application).use { classifier ->
                var measuredFrames = 0
                var referenceBounds: List<NormalizedLandmark>? = null
                val paired = InstrumentationRegistry.getArguments().getString("pairedCropDiagnostic") == "true"
                for (phase in if (paired) listOf("clear", "covered") else listOf("clear")) {
                if (phase == "covered") {
                    checkNotNull(referenceBounds) { "No clear-face reference crop was established." }
                    Log.i("DriveAlertCropDiagnostic", "CLEAR_COMPLETE cover_mouth_now")
                    Thread.sleep(15_000)
                }
                repeat(sampleCount) { sample ->
                    val connection = URL("http://${device.lastKnownIp}:80/capture").openConnection() as HttpURLConnection
                    connection.authorizeDevice(device.sessionToken)
                    connection.connectTimeout = 3_000
                    connection.readTimeout = 3_000
                    connection.useCaches = false
                    val bitmap = try {
                        assertEquals(200, connection.responseCode)
                        connection.requireDeviceResponse(device.deviceId, device.sessionToken)
                        connection.inputStream.use { requireNotNull(BitmapFactory.decodeStream(it)) }
                    } finally { connection.disconnect() }
                    try {
                        val image = BitmapImageBuilder(bitmap).build()
                        try {
                        val detection = if (phase == "covered") null else model.detect(image)
                        detection?.let {
                            assertEquals("Each detected face needs expression output",
                                it.faceLandmarks().size, it.faceBlendshapes().orElse(emptyList()).size)
                        }
                        detection?.faceBlendshapes()?.orElse(emptyList())?.forEachIndexed { index, categories ->
                            val scores = categories.associate { it.categoryName() to it.score() }
                            for (name in listOf("eyeBlinkLeft", "eyeBlinkRight", "eyeLookDownLeft", "eyeLookDownRight")) {
                                assertTrue("Missing or invalid expression $name", scores[name]?.let { it.isFinite() && it in 0f..1f } == true)
                            }
                            Log.i("DriveAlertCropDiagnostic", "sample=$sample faceIndex=$index " +
                                "blinkL=${scores["eyeBlinkLeft"]} blinkR=${scores["eyeBlinkRight"]} " +
                                "downL=${scores["eyeLookDownLeft"]} downR=${scores["eyeLookDownRight"]}")
                        }
                        val faces = if (phase == "covered") listOf(FaceObservation(sample.toLong(), bitmap.width, bitmap.height,
                            checkNotNull(referenceBounds))) else checkNotNull(detection).faceLandmarks().map { points ->
                            FaceObservation(sample.toLong(), bitmap.width, bitmap.height,
                                points.map { NormalizedLandmark(it.x().toDouble(), it.y().toDouble()) })
                        }
                        val eligible = if (faces.size == 1) faces else faces.filter { face -> DriverRegion.bounds(face)?.let {
                            DriverRegion.DEFAULT.containsCenter(it) && DriverRegion.DEFAULT.intersection(it) / it.area >= 0.8
                        } == true }
                        Log.i("DriveAlertCropDiagnostic", "phase=$phase sample=$sample faces=${faces.size} eligible=${eligible.size}")
                        if (eligible.size == 1) {
                            measuredFrames++
                            val landmarks = eligible.single().landmarks
                            exportDirectory?.let { directory ->
                                File(directory, "$phase-$sample-frame.png").outputStream().use {
                                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                                }
                                val metadata = JSONObject()
                                    .put("frameWidth", bitmap.width).put("frameHeight", bitmap.height)
                                    .put("fixedReferenceBounds", phase == "covered")
                                    .put("landmarks", JSONArray().apply {
                                        landmarks.forEach { put(JSONArray(listOf(it.x, it.y))) }
                                    })
                                File(directory, "$phase-$sample-landmarks.json").writeText(metadata.toString())
                            }
                            if (phase == "clear") {
                                // Rectangle only, kept in memory for crop comparison.
                                // It is never a detection/assessment of the covered frame.
                                val bounds = checkNotNull(DriverRegion.bounds(eligible.single()))
                                referenceBounds = listOf(NormalizedLandmark(bounds.left, bounds.top),
                                    NormalizedLandmark(bounds.right, bounds.top),
                                    NormalizedLandmark(bounds.right, bounds.bottom), NormalizedLandmark(bounds.left, bounds.bottom))
                            }
                            for (padding in listOf(0.0, 0.1, 0.2, 0.3)) {
                            val result = requireNotNull(classifier.infer(bitmap, landmarks, bitmap.width, bitmap.height, padding,
                                onPreparedInput = { input ->
                                    exportDirectory?.let { directory ->
                                        File(directory, "$phase-$sample-input-$padding.png").outputStream().use {
                                            check(input.compress(Bitmap.CompressFormat.PNG, 100, it))
                                        }
                                    }
                                }))
                            val crop = requireNotNull(FaceCropCalculator.calculate(landmarks, bitmap.width, bitmap.height, padding))
                            Log.i("DriveAlertCropDiagnostic", "phase=$phase fixedCrop=${phase == "covered"} sample=$sample frame=${bitmap.width}x${bitmap.height} " +
                                "padding=$padding crop=${crop.width}x${crop.height} mask=${result.probabilities.mask} " +
                                "leftOpen=${result.probabilities.leftEyeOpen} rightOpen=${result.probabilities.rightEyeOpen}")
                            }
                        }
                        } finally { image.close() }
                    } finally { bitmap.recycle() }
                }
                }
                assertTrue("No isolated face found in camera captures", measuredFrames > 0)
            }
        }
    }
}
