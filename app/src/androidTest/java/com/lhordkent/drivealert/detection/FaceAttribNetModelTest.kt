package com.lhordkent.drivealert.detection

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lhordkent.drivealert.detection.face.FaceAttribNetClassifier
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaceAttribNetModelTest {
    @Test
    fun packagedQuantizedModelLoadsAndReturnsFiveFiniteProbabilities() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(127, 127, 127))
        }
        val landmarks = listOf(
            NormalizedLandmark(0.2, 0.2),
            NormalizedLandmark(0.8, 0.2),
            NormalizedLandmark(0.8, 0.8),
            NormalizedLandmark(0.2, 0.8),
        )

        FaceAttribNetClassifier(context).use { classifier ->
            val inference = classifier.infer(bitmap, landmarks, bitmap.width, bitmap.height)
            assertNotNull(inference)
            val values = checkNotNull(inference).probabilities.run {
                listOf(leftEyeOpen, rightEyeOpen, eyeglasses, mask, sunglasses)
            }
            assertTrue(values.all { it.isFinite() && it in 0f..1f })
        }
        bitmap.recycle()
    }
}
