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
import com.lhordkent.drivealert.detection.frame.FrameLuminanceSampler
import com.lhordkent.drivealert.monitoring.FaceLightingSample
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
        lightingSample: FaceLightingSample?,
        tracking: DriverTrackingState,
        configurationRevision: Long,
    ) -> Unit,
    private val onError: (Throwable) -> Unit = {},
    modelAssetPath: String = MODEL_ASSET_PATH,
    maxFaces: Int = 4,
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "drivealert-face-landmarker")
    }
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val landmarker: FaceLandmarker
    private data class Configuration(val region: DriverRegion?, val revision: Long)
    @Volatile private var configuration = Configuration(null, 0)
    private var appliedRevision = -1L
    private val selector = DriverFaceSelector(capacity = maxFaces)
    private var lastTargetGeneration = -1L

    @Synchronized
    fun configure(region: DriverRegion?): Long {
        configuration = Configuration(region, configuration.revision + 1)
        return configuration.revision
    }

    private fun resetFaceCaches() {
        attributeTracker.reset()
        lastAttributeAttemptTimestampMs = null
        lightingSample = null
        lastLightingAttemptTimestampMs = null
    }

    private val attributeTracker = FaceAttributeShadowTracker()
    private var attributeClassifier: FaceAttribNetClassifier? = null
    private var attributeFailureReason: String? = null
    private var lastAttributeAttemptTimestampMs: Long? = null
    private var lightingSample: FaceLightingSample? = null
    private var lastLightingAttemptTimestampMs: Long? = null

    init {
        require(maxFaces >= 2) { "Driver selection requires detection of competing faces." }
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(maxFaces)
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
        val submittedConfiguration = configuration
        return enqueue(
            work = {
                var bitmap: Bitmap? = null
                try {
                    bitmap = bitmapFactory()
                    process(bitmap, width, height, timestampMs, submittedConfiguration)
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

    private fun process(bitmap: Bitmap, width: Int, height: Int, timestampMs: Long, submittedConfiguration: Configuration) {
        if (submittedConfiguration.revision != configuration.revision) return
        if (appliedRevision != submittedConfiguration.revision) {
            selector.reset()
            resetFaceCaches()
            appliedRevision = submittedConfiguration.revision
        }
        val image = BitmapImageBuilder(bitmap).build()
        try {
            val result = landmarker.detectForVideo(image, timestampMs)
            val matrices = result.facialTransformationMatrixes().orElse(emptyList())
            val candidates = result.faceLandmarks().mapIndexed { index, points ->
                FaceObservation(timestampMs, width, height,
                    points.map { NormalizedLandmark(it.x().toDouble(), it.y().toDouble()) },
                    matrices.getOrNull(index))
            }
            val selection = selector.select(timestampMs, candidates, submittedConfiguration.region, width, height)
            if (!selection.tracking.locked || lastTargetGeneration != selection.tracking.generation) resetFaceCaches()
            lastTargetGeneration = selection.tracking.generation
            val observation = selection.observation
            val landmarks = observation?.landmarks
            val attributes = processFaceAttributes(bitmap, landmarks, width, height, timestampMs)
            if (landmarks == null) {
                lightingSample = null
                lastLightingAttemptTimestampMs = null
            } else if (lastLightingAttemptTimestampMs?.let { timestampMs - it >= LIGHTING_INTERVAL_MS } != false) {
                lastLightingAttemptTimestampMs = timestampMs
                lightingSample = FrameLuminanceSampler.sampleFace(bitmap, landmarks, timestampMs)
            }
            onObservation(timestampMs, observation, attributes, lightingSample, selection.tracking, submittedConfiguration.revision)
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
        private const val LIGHTING_INTERVAL_MS = 200L
    }
}
