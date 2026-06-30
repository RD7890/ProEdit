package com.rohan.proedit.processing

import android.graphics.Bitmap

/**
 * JNI bridge to the NDK C++ image processing engine.
 * All functions are thread-safe when operating on different bitmaps/arrays.
 */
object NativeProcessor {

    init {
        System.loadLibrary("proedit-native")
    }

    /**
     * Apply adjustments to [bitmap] in-place.
     * [mask] is nullable — if null, applies to the entire image.
     * All float params: -1.0 (min) to +1.0 (max), 0.0 = no change.
     */
    external fun applyAdjustmentsInPlace(
        bitmap: Bitmap,
        mask: ByteArray?,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        temperature: Float,
        highlights: Float,
        shadows: Float,
        sharpness: Float,
        ambiance: Float
    )

    /**
     * Apply a preset filter to [bitmap] in-place.
     * filterIndex: 0=Original, 1=Vivid, 2=Dramatic, 3=B&W, 4=Cinematic, 5=Aqua, 6=Matte, 7=Golden
     */
    external fun applyFilterInPlace(bitmap: Bitmap, filterIndex: Int)

    /**
     * Returns an ARGB IntArray (red overlay) for mask visualization.
     * Suitable for Bitmap.createBitmap(intArray, w, h, Config.ARGB_8888).
     */
    external fun getMaskOverlay(mask: ByteArray, width: Int, height: Int): IntArray

    /** Paint a soft-edged circle on the mask. */
    external fun paintMask(
        mask: ByteArray, width: Int, height: Int,
        cx: Float, cy: Float, radius: Float, add: Boolean
    )

    /**
     * Smart brush: BFS flood-fill from (cx,cy) selecting pixels by color similarity.
     * [pixels] = IntArray from Bitmap.getPixels(), ARGB_8888.
     */
    external fun smartBrushSelect(
        pixels: IntArray, mask: ByteArray,
        width: Int, height: Int,
        cx: Float, cy: Float, radius: Float,
        tolerance: Float, add: Boolean
    )

    /** Radial gradient mask: full at center, fades to zero at outerRadius. */
    external fun radialMask(
        mask: ByteArray, width: Int, height: Int,
        centerX: Float, centerY: Float,
        innerRadius: Float, outerRadius: Float,
        inverted: Boolean
    )

    /** Linear gradient mask: full at (startX,startY) → zero at (endX,endY). */
    external fun linearGradientMask(
        mask: ByteArray, width: Int, height: Int,
        startX: Float, startY: Float,
        endX: Float, endY: Float
    )

    /** Color-range mask: select pixels similar to (tR,tG,tB). */
    external fun colorRangeMask(
        pixels: IntArray, mask: ByteArray,
        width: Int, height: Int,
        tR: Int, tG: Int, tB: Int,
        tolerance: Float
    )

    /** Invert all mask values (0↔255). */
    external fun invertMask(mask: ByteArray)

    /** Clear mask to 0 (fillFull=false) or 255 (fillFull=true). */
    external fun clearMask(mask: ByteArray, fillFull: Boolean)
}
