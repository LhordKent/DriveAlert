package com.lhordkent.drivealert.detection.face

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.lhordkent.drivealert.detection.frame.FramePacket
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
    private val onObservation: (timestampMs: Long, observation: FaceObservation?) -> Unit,
    private val onError: (Throwable) -> Unit = {},
    modelAssetPath: String = MODEL_ASSET_PATH,
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "drivealert-face-landmarker")
    }
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val landmarker: FaceLandmarker

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
    }

    /** Returns false when the frame was intentionally dropped because inference is busy. */
    fun submit(frame: FramePacket<MPImage>): Boolean {
        if (closed.get() || !busy.compareAndSet(false, true)) {
            frame.release()
            return false
        }
        return enqueue(
            work = {
                try {
                    process(frame)
                } finally {
                    frame.release()
                }
            },
            onRejected = frame.release,
        )
    }

    /**
     * Claims the processor before constructing an MPImage. The source frame is
     * always released, including when inference is busy or the executor closes.
     */
    fun submitLazy(
        width: Int,
        height: Int,
        timestampMs: Long,
        imageFactory: () -> MPImage,
        releaseSource: () -> Unit,
    ): Boolean {
        if (closed.get() || !busy.compareAndSet(false, true)) {
            releaseSource()
            return false
        }
        return enqueue(
            work = {
                var image: MPImage? = null
                try {
                    image = imageFactory()
                    process(
                        FramePacket(
                            payload = image,
                            width = width,
                            height = height,
                            timestampMs = timestampMs,
                        ),
                    )
                } finally {
                    try {
                        image?.close()
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

    private fun process(frame: FramePacket<MPImage>) {
        val result = landmarker.detectForVideo(frame.payload, frame.timestampMs)
        val landmarks = result.faceLandmarks().firstOrNull()?.map {
            NormalizedLandmark(it.x().toDouble(), it.y().toDouble())
        }
        val observation = landmarks?.let {
            FaceObservation(
                timestampMs = frame.timestampMs,
                frameWidth = frame.width,
                frameHeight = frame.height,
                landmarks = it,
                facialTransformationMatrix = result.facialTransformationMatrixes()
                    .orElse(emptyList())
                    .firstOrNull(),
            )
        }
        onObservation(frame.timestampMs, observation)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.execute { landmarker.close() }
        executor.shutdown()
    }

    companion object {
        const val MODEL_ASSET_PATH = "face_landmarker.task"
    }
}
