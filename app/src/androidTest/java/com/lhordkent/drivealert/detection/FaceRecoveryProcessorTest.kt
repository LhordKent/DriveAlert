package com.lhordkent.drivealert.detection

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.detection.face.DriverRegion
import com.lhordkent.drivealert.detection.face.DriverTrackingStatus
import com.lhordkent.drivealert.detection.face.MediaPipeFaceLandmarkerProcessor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

/** Native model smoke test: no Activity, accounts, Room or application-data resets. */
class FaceRecoveryProcessorTest {
    @Test fun bothNativeTrackersRejectAnEmptyDriverArea() {
        val finished = CountDownLatch(1)
        val released = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val processor = MediaPipeFaceLandmarkerProcessor(
            InstrumentationRegistry.getInstrumentation().targetContext,
            onObservation = { _, observation, attributes, lighting, tracking, _ ->
                try {
                    assertNull(observation)
                    assertNull(attributes.probabilities)
                    assertNull(lighting)
                    assertEquals(DriverTrackingStatus.LOST, tracking.status)
                    assertEquals(0, tracking.detectedFaceCount)
                } catch (error: Throwable) { failure.set(error) }
                finally { finished.countDown() }
            },
            onError = { failure.set(it); finished.countDown() },
        )
        try {
            processor.configure(DriverRegion.DEFAULT)
            assertTrue(processor.submitLazy(320, 240, 100,
                { Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888) },
                { released.countDown() }))
            assertTrue("Native inference timed out", finished.await(30, TimeUnit.SECONDS))
            failure.get()?.let { throw it }
            assertTrue("Frame was not released", released.await(5, TimeUnit.SECONDS))
        } finally {
            processor.close()
        }
    }
}
