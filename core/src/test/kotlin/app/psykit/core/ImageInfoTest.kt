package app.psykit.core

import app.psykit.core.image.ImageInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageInfoTest {
    private fun bytes(name: String) = javaClass.getResourceAsStream("/images/$name")!!.readBytes()
    private fun info(name: String) = assertNotNull(ImageInfo.parse(bytes(name)), name)

    @Test fun animatedWebp() {
        val i = info("anim.webp")
        assertEquals("WebP", i.format); assertEquals(64, i.width); assertEquals(48, i.height)
        assertEquals(5, i.frames); assertEquals(500, i.durationMs); assertEquals(10.0, i.fps, 0.01)
        assertTrue(ImageInfo.isAnimatedContainer(bytes("anim.webp")))
    }

    @Test fun animatedWebpWithLargeIccIsStillAnimated() {
        assertTrue(ImageInfo.isAnimatedContainer(bytes("anim_icc.webp")))
        assertEquals(5, info("anim_icc.webp").frames)
    }

    @Test fun stillWebp() {
        val a = info("still.webp"); assertEquals(1, a.frames); assertEquals(64, a.width)
        val b = info("still_lossy.webp"); assertEquals(40, b.width); assertEquals(30, b.height)
        assertFalse(ImageInfo.isAnimatedContainer(bytes("still.webp")))
    }

    @Test fun gif() {
        val i = info("anim.gif")
        assertEquals(3, i.frames); assertEquals(600, i.durationMs); assertEquals(1, i.zeroDelayFrames)
        assertTrue(ImageInfo.isAnimatedContainer(bytes("anim.gif")))
    }

    @Test fun pngAndApng() {
        assertEquals(0, info("still.png").apngFrames); assertEquals(true, info("still.png").alpha)
        assertEquals(4, info("apng.png").apngFrames)
        assertFalse(ImageInfo.isAnimatedContainer(bytes("apng.png")))
    }

    @Test fun jpeg() {
        val i = info("photo.jpg"); assertEquals(30, i.width); assertEquals(20, i.height)
    }
}
