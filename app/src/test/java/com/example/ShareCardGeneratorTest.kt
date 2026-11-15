package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.ShareCardGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShareCardGeneratorTest {

    @Test
    fun renderCardBitmap_hasExactInstagramStoryDimensionsAndContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = ShareCardGenerator.renderCardBitmap(
            context = context,
            badgeLabel = "الورد القرآني اليومي",
            title = "سورة الفاتحة",
            arabicBody = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ﴿١﴾ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ﴿٢﴾",
            source = "مكية • أم الكتاب",
            cityAndDate = "الموصل • 1448 هـ"
        )

        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
        assertEquals(9f / 16f, bitmap.width.toFloat() / bitmap.height.toFloat(), 0.001f)

        // Verify the real app launcher icon bitmap decodes cleanly for corner rendering
        val iconBitmap = ShareCardGenerator.loadAppIconBitmap(context)
        assertNotNull(iconBitmap)
        assertTrue((iconBitmap?.width ?: 0) > 0 && (iconBitmap?.height ?: 0) > 0)
        iconBitmap?.recycle()
        bitmap.recycle()
    }

    @Test
    fun createPureImageShareIntent_sharesPngImageWithoutExtraText() {
        val fakeUri = Uri.parse("content://com.aistudio.vakittesbih.kzqwpm.provider/cache/shared_cards/story.png")
        val intent = ShareCardGenerator.createPureImageShareIntent(fakeUri)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertNotNull(intent.clipData)
        assertFalse(intent.hasExtra(Intent.EXTRA_TEXT))
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }
}
