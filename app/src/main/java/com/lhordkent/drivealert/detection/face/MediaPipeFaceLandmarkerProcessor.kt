package com.lhordkent.drivealert.detection.face

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.lhordkent.drivealert.detection.model.FaceAttributeResult
import com.lhordkent.drivealert.detection.model.FaceObservation
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-threaded MediaPipe boundary. A busy processor drops stale frames rather than
 * accumulating latency. Results are delivered on the processor thread.
 */
class MediaPipeFaceLandmarkerProcessor(
    context: Context,
    private val onObservation: (
        timestampMs: Long,
        observation: FaceObservation?,
        faceAttributes: FaceAttributeResult,
    ) -> Unit,
    private val onError: (Throwable) -> Unit = {},
    modelAssetPath: String = MODEL_ASSET_PATH,
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "drivealert-face-landmarker")
    }
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val landmarker: FaceLandmarker
    private val attributeTracker = FaceAttributeShadowTracker()
    private var attributeClassifier: FaceAttribNetClassifier? = null
    private var attributeFailureReason: String? = null
    private var lastAttributeAttemptTimestampMs: Long? = null

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(false)
            .setOutputFacialTransformationMatrixes(true)
            .build()
        landmarker = FaceLandmarker.createFromOptions(context.applicationContext, options)
        runCatching { FaceAttribNetClassifier(context.applicationContext) }
            .onSuccess { attributeClassifier = it }
            .onFailure { attributeFailureReason = it.message ?: "FaceAttribNet could not be initialized." }
    }

    /**
     * Claims the processor before constructing an MPImage. The source frame is
     * always released, including when inference is busy or the executor closes.
     */
    fun submitLazy(
        width: Int,
        height: Int,
        timestampMs: Long,
        bitmapFactory: () -> Bitmap,
        releaseSource: () -> Unit,
    ): Boolean {
        if (closed.get() || !busy.compareAndSet(false, true)) {
            releaseSource()
            return false
        }
        return enqueue(
            work = {
                var bitmap: Bitmap? = null
                try {
                    bitmap = bitmapFactory()
                    process(bitmap, width, height, timestampMs)
                } finally {
                    try {
                        bitmap?.recycle()
                    } finally {
                        releaseSource()
                    }
                }
            },
            onRejected = releaseSource,
        )
    }

    private fun enqueue(work: () -> Unit, onRejected: () -> Unit): Boolean {
        return try {
            executor.execute {
                try {
                    work()
                } catch (throwable: Throwable) {
                    onError(throwable)
                } finally {
                    busy.set(false)
                }
            }
            true
        } catch (throwable: Throwable) {
            busy.set(false)
            onRejected()
            if (!closed.get()) onError(throwable)
            false
        }
    }

    private fun process(bitmap: Bitmap, width: Int, height: Int, timestampMs: Long) {
        val image = BitmapImageBuilder(bitmap).build()
        try {
            val result = landmarker.detectForVideo(image, timestampMs)
            val landmarks = result.faceLandmarks().firstOrNull()?.map { landmark ->
                NormalizedLandmark(landmark.x().toDouble(), landmark.y().toDouble())
            }
            val observation = landmarks?.let {
                FaceObservation(
                    timestampMs = timestampMs,
                    frameWidth = width,
                    frameHeight = height,
                    landmarks = it,
                    facialTransformationMatrix = result.facialTransformationMatrixes()
                        .orElse(emptyList())
                        .firstOrNull(),
                )
            }
            val attributes = processFaceAttributes(bitmap, landmarks, width, height, timestampMs)
            onObservation(timestampMs, observation, attributes)
        } finally {
            image.close()
        }
    }

    private fun processFaceAttributes(
        bitmap: Bitmap,
        landmarks: List<NormalizedLandmark>?,
        width: Int,
        height: Int,
        timestampMs: Long,
    ): FaceAttributeResult {
        attributeFailureReason?.let { return attributeTracker.disable(it) }
        val classifier = attributeClassifier
            ?: return attributeTracker.disable("FaceAttribNet is unavailable.")
        val lastAttempt = lastAttributeAttemptTimestampMs
        val due = lastAttempt == null || timestampMs - lastAttempt >= ATTRIBUTE_INTERVAL_MS
        if (landmarks == null || !due) return attributeTracker.snapshot(timestampMs)
        lastAttributeAttemptTimestampMs = timestampMs
        return try {
            val inference = classifier.infer(bitmap, landmarks, width, height)
                ?: return attributeTracker.snapshot(timestampMs)
            attributeTracker.accept(timestampMs, inference.probabilities, inference.durationMs)
        } catch (error: Throwable) {
            runCatching { classifier.close() }
            attributeClassifier = null
            attributeFailureReason = error.message ?: "FaceAttribNet inference failed."
            attributeTracker.disable(checkNotNull(attributeFailureReason))
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.execute {
            attributeClassifier?.close()
            attributeClassifier = null
            landmarker.close()
        }
        executor.shutdown()
    }

    companion object {
        const val MODEL_ASSET_PATH = "face_landmarker.task"
        private const val ATTRIBUTE_INTERVAL_MS = 500L
    }
}
