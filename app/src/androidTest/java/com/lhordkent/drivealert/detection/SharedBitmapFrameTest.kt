package com.lhordkent.drivealert.detection

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.lhordkent.drivealert.detection.frame.SharedBitmapFrame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedBitmapFrameTest {
    @Test
    fun releasedFrameCannotBeResurrectedOrRecycleComposeBitmap() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val owner = SharedBitmapFrame.create(bitmap)
        val consumer = owner.tryRetain()

        assertSame(owner, consumer)
        owner.release()
        assertFalse(bitmap.isRecycled)

        consumer!!.release()
        assertFalse(bitmap.isRecycled)
        assertNull(owner.tryRetain())
    }

    @Test
    fun closingMediaPipeCopyCannotRecyclePreviewBitmap() {
        val preview = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val inferenceCopy = preview.copy(Bitmap.Config.ARGB_8888, false)
        val image = BitmapImageBuilder(inferenceCopy).build()

        image.close()

        assertFalse(preview.isRecycled)
    }
}
