package com.lhordkent.drivealert.detection.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.lhordkent.drivealert.BuildConfig
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.components.containers.Category
import com.lhordkent.drivealert.detection.model.FaceAttributeResult
import com.lhordkent.drivealert.detection.model.FaceObservation
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import com.lhordkent.drivealert.detection.frame.FrameLuminanceSampler
import com.lhordkent.drivealert.detection.measurement.FacialMeasurementCalculator
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
    private val modelAssetPath: String = MODEL_ASSET_PATH,
    maxFaces: Int = 4,
) : Closeable {
    private val applicationContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "drivealert-face-landmarker")
    }
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    // Accessed only by the executor, including closure. Merely opening the app,
    // configuring alignment, or closing an unused processor must not load native models.
    private val landmarker = lazy(LazyThreadSafetyMode.NONE) {
        val model = createLandmarker(maxFaces)
        runCatching { FaceAttribNetClassifier(context.applicationContext) }
            .onSuccess { attributeClassifier = it }
            .onFailure { attributeFailureReason = it.message ?: "FaceAttribNet could not be initialized." }
        model
    }
    // A multi-face graph keeps invoking face detection until its capacity is
    // filled. Keep the original single-face VIDEO tracker warm as well: it can
    // follow the model's previous face crop when a small, partly covered face
    // drops out of full-scene detection. Both graphs see THIS frame, at the same
    // full-frame coordinates (including the pose matrix); no old landmarks are reused.
    private val continuityLandmarker = lazy(LazyThreadSafetyMode.NONE) { createLandmarker(1) }

    private fun createLandmarker(faceCount: Int): FaceLandmarker = FaceLandmarker.createFromOptions(
        applicationContext,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetPath).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumFaces(faceCount)
            .setMinFaceDetectionConfidence(MIN_FACE_CONFIDENCE)
            .setMinFacePresenceConfidence(MIN_FACE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_FACE_CONFIDENCE)
            // Numeric debug evidence only; these expression scores do not yet
            // participate in calibration or alarm decisions.
            .setOutputFaceBlendshapes(BuildConfig.DEBUG)
            .setOutputFacialTransformationMatrixes(true)
            .build(),
    )
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
            val result = landmarker.value.detectForVideo(image, timestampMs)
            val matrices = result.facialTransformationMatrixes().orElse(emptyList())
            val sceneExpressions = result.faceBlendshapes().orElse(emptyList())
            var continuityExpressions: List<List<Category>> = emptyList()
            val fullSceneCandidates = result.faceLandmarks().mapIndexed { index, points ->
                FaceObservation(timestampMs, width, height,
                    points.map { NormalizedLandmark(it.x().toDouble(), it.y().toDouble()) },
                    matrices.getOrNull(index))
            }
            val continuityCandidates = if (submittedConfiguration.region != null) {
                val tracked = continuityLandmarker.value.detectForVideo(image, timestampMs)
                continuityExpressions = tracked.faceBlendshapes().orElse(emptyList())
                val trackedMatrices = tracked.facialTransformationMatrixes().orElse(emptyList())
                tracked.faceLandmarks().mapIndexed { index, points ->
                    FaceObservation(timestampMs, width, height,
                        points.map { NormalizedLandmark(it.x().toDouble(), it.y().toDouble()) },
                        trackedMatrices.getOrNull(index))
                }
            } else emptyList()
            val candidates = recoverFaceCandidates(fullSceneCandidates, continuityCandidates)
            val selection = selector.select(timestampMs, candidates, submittedConfiguration.region, width, height)
            if (!selection.tracking.locked || lastTargetGeneration != selection.tracking.generation) resetFaceCaches()
            lastTargetGeneration = selection.tracking.generation
            val observation = selection.observation
            val landmarks = observation?.landmarks
            val attributes = processFaceAttributes(bitmap, landmarks, width, height, timestampMs)
            if (BuildConfig.DEBUG && attributes.timestampMs == timestampMs) {
                Log.d("DriveAlertVisibility", "track=${selection.tracking.status} faces=${candidates.size} " +
                    "sceneFaces=${fullSceneCandidates.size} continuityFaces=${continuityCandidates.size} " +
                    "mask=${attributes.probabilities?.mask} sunglasses=${attributes.probabilities?.sunglasses} " +
                    "mouth=${attributes.lowerFaceObstructed} eyes=${attributes.eyeRegionObstructed}")
                val selectedIndex = candidates.indexOfFirst { it === observation }
                val expressions = (if (fullSceneCandidates.isEmpty()) continuityExpressions else sceneExpressions)
                    .getOrNull(selectedIndex)?.associate { it.categoryName() to it.score() }
                // Raw selected-face geometry remains observable when alignment,
                // lighting or regional gates deliberately withhold assessment.
                // Diagnostic values must never bypass those gates in the engine.
                val rawMeasurements = observation?.let(FacialMeasurementCalculator::calculate)
                Log.d("DriveAlertEyeExpression", "sourceTs=$timestampMs track=${selection.tracking.status} " +
                    "rawEar=${rawMeasurements?.ear} rawPitch=${rawMeasurements?.rawHeadPitchDegrees} " +
                    "blinkL=${expressions?.get("eyeBlinkLeft")} blinkR=${expressions?.get("eyeBlinkRight")} " +
                    "downL=${expressions?.get("eyeLookDownLeft")} downR=${expressions?.get("eyeLookDownRight")} " +
                    "squintL=${expressions?.get("eyeSquintLeft")} squintR=${expressions?.get("eyeSquintRight")}")
            }
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
            if (landmarker.isInitialized()) landmarker.value.close()
            if (continuityLandmarker.isInitialized()) continuityLandmarker.value.close()
        }
        executor.shutdown()
    }

    companion object {
        const val MODEL_ASSET_PATH = "face_landmarker.task"
        const val MIN_FACE_CONFIDENCE = 0.3f
        private const val ATTRIBUTE_INTERVAL_MS = 500L
        private const val LIGHTING_INTERVAL_MS = 200L
    }
}

/** A recovery must never hide a competitor, out-of-area face, or saturated scene.
 * The selector still enforces region eligibility, continuity and acquisition. */
internal fun recoverFaceCandidates(
    fullScene: List<FaceObservation>,
    continuity: List<FaceObservation>,
): List<FaceObservation> = if (fullScene.isEmpty()) continuity else fullScene
