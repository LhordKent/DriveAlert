package com.lhordkent.drivealert.monitoring

import android.graphics.Bitmap
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.detection.face.DriverRegion
import com.lhordkent.drivealert.detection.face.MediaPipeFaceLandmarkerProcessor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class VisionStartupTest {
    @Test fun unusedProcessorCanBeCreatedConfiguredAndClosedOnMainWithoutLoadingModels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val processor = MediaPipeFaceLandmarkerProcessor(
                instrumentation.targetContext,
                onObservation = { _, _, _, _, _, _ -> fail("Unused processor must not infer") },
                // A nonexistent asset would throw if constructor/configuration touched the model.
                modelAssetPath = "startup-test-missing-model.task",
            )
            assertEquals(1L, processor.configure(DriverRegion.DEFAULT))
            processor.close()
            var released = false
            assertFalse(processor.submitLazy(100, 100, 0,
                { error("Closed processor must not request a bitmap") }, { released = true }))
            assertTrue(released)
        }
    }

    @Test fun firstFrameInitializesOnWorkerAndReleasesSourceWhenModelLoadFails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val released = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val mainThread = AtomicReference<Boolean?>()
        val processor = MediaPipeFaceLandmarkerProcessor(
            instrumentation.targetContext,
            onObservation = { _, _, _, _, _, _ -> fail("Missing model cannot infer") },
            onError = {
                failure.set(it)
                mainThread.set(Looper.myLooper() == Looper.getMainLooper())
                failed.countDown()
            },
            modelAssetPath = "startup-test-missing-model.task",
        )
        try {
            instrumentation.runOnMainSync {
                assertTrue(processor.submitLazy(100, 100, 0,
                    { Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888) }, released::countDown))
            }
            assertTrue(failed.await(30, TimeUnit.SECONDS))
            assertTrue(released.await(5, TimeUnit.SECONDS))
            assertNotNull(failure.get())
            assertEquals(false, mainThread.get())
        } finally { processor.close() }
    }
}
