package com.rohan.proedit.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohan.proedit.processing.NativeProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class MaskMode { NONE, BRUSH, SMART_BRUSH, RADIAL, LINEAR, COLOR_RANGE }
enum class EditorTab  { TUNE, FILTERS, MASK }

data class AdjustmentState(
    val brightness:  Float = 0f,
    val contrast:    Float = 0f,
    val saturation:  Float = 0f,
    val temperature: Float = 0f,
    val highlights:  Float = 0f,
    val shadows:     Float = 0f,
    val sharpness:   Float = 0f,
    val ambiance:    Float = 0f,
)

data class EditorUiState(
    val originalBitmap:  Bitmap?         = null,
    val displayBitmap:   Bitmap?         = null,
    val overlayBitmap:   Bitmap?         = null,
    val mask:            ByteArray?      = null,
    val hasMask:         Boolean         = false,
    val adjustments:     AdjustmentState = AdjustmentState(),
    val activeFilter:    Int             = 0,
    val activeTab:       EditorTab       = EditorTab.TUNE,
    val maskMode:        MaskMode        = MaskMode.BRUSH,
    val brushSize:       Float           = 60f,
    val brushAdding:     Boolean         = true,
    val smartTolerance:  Float           = 0.35f,
    val isProcessing:    Boolean         = false,
    val isSaving:        Boolean         = false,
    val saveError:       String?         = null,
    val savedPath:       String?         = null,
)

class EditorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var applyJob:   Job? = null
    /** Tracked separately so we can cancel before launching a new overlay render. */
    private var overlayJob: Job? = null

    // ── Image loading ─────────────────────────────────────────────────────

    fun loadImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                val stream = context.contentResolver.openInputStream(uri)
                val opts   = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val raw = BitmapFactory.decodeStream(stream, null, opts)
                stream?.close()

                if (raw == null) { _uiState.update { it.copy(isProcessing = false) }; return@launch }

                // Downscale very large images (keep within 4096 px on longest side)
                val maxSide = 4096
                val scaled = if (raw.width > maxSide || raw.height > maxSide) {
                    val scale = maxSide.toFloat() / maxOf(raw.width, raw.height)
                    Bitmap.createScaledBitmap(
                        raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true
                    ).also { if (it !== raw) raw.recycle() }
                } else raw

                val display = scaled.copy(Bitmap.Config.ARGB_8888, true)
                _uiState.update {
                    it.copy(
                        originalBitmap = scaled,
                        displayBitmap  = display,
                        mask           = null,
                        hasMask        = false,
                        overlayBitmap  = null,
                        adjustments    = AdjustmentState(),
                        activeFilter   = 0,
                        isProcessing   = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    // ── Adjustments ───────────────────────────────────────────────────────

    fun updateAdjustment(block: AdjustmentState.() -> AdjustmentState) {
        _uiState.update { it.copy(adjustments = it.adjustments.block()) }
        scheduleApply()
    }

    private fun scheduleApply() {
        applyJob?.cancel()
        applyJob = viewModelScope.launch {
            delay(60) // debounce 60 ms
            applyAdjustments()
        }
    }

    private suspend fun applyAdjustments() {
        val state    = _uiState.value
        val original = state.originalBitmap ?: return
        val adj      = state.adjustments

        withContext(Dispatchers.Default) {
            val working = original.copy(Bitmap.Config.ARGB_8888, true)
            NativeProcessor.applyAdjustmentsInPlace(
                bitmap      = working,
                mask        = if (state.hasMask) state.mask?.copyOf() else null,
                brightness  = adj.brightness,
                contrast    = adj.contrast,
                saturation  = adj.saturation,
                temperature = adj.temperature,
                highlights  = adj.highlights,
                shadows     = adj.shadows,
                sharpness   = adj.sharpness,
                ambiance    = adj.ambiance,
            )
            // Do NOT recycle the old bitmap immediately — Compose's ImageBitmap may
            // still reference it on the render thread. Let GC collect it.
            _uiState.update { cur -> cur.copy(displayBitmap = working) }
        }
    }

    // ── Filters ───────────────────────────────────────────────────────────

    fun applyFilter(filterIndex: Int) {
        val original = _uiState.value.originalBitmap ?: return
        _uiState.update { it.copy(activeFilter = filterIndex) }

        viewModelScope.launch(Dispatchers.Default) {
            val working = original.copy(Bitmap.Config.ARGB_8888, true)
            if (filterIndex > 0) {
                NativeProcessor.applyFilterInPlace(working, filterIndex)
            }
            _uiState.update { cur -> cur.copy(displayBitmap = working) }
        }
    }

    // ── Mask tools ────────────────────────────────────────────────────────

    fun enterMaskMode() {
        val state    = _uiState.value
        val original = state.originalBitmap ?: return
        val w = original.width; val h = original.height
        val mask = state.mask ?: ByteArray(w * h) { 0 }
        _uiState.update { it.copy(mask = mask, activeTab = EditorTab.MASK) }
        refreshOverlayImmediate(mask, w, h)
    }

    fun exitMaskMode(apply: Boolean) {
        overlayJob?.cancel()
        if (!apply) {
            _uiState.update { it.copy(activeTab = EditorTab.TUNE) }
            return
        }
        _uiState.update { it.copy(hasMask = true, activeTab = EditorTab.TUNE) }
        scheduleApply()
    }

    /**
     * Called on every drag point during brush painting.
     * Paints to the mask synchronously (fast JNI), then schedules a DEBOUNCED overlay
     * refresh — prevents launching a coroutine per touch event (60+ per second).
     */
    fun paintBrush(x: Float, y: Float) {
        val state = _uiState.value
        val mask  = state.mask ?: return
        val bmp   = state.originalBitmap ?: return
        // Write to mask on the calling (main) thread — fast JNI, no coroutine needed
        NativeProcessor.paintMask(mask, bmp.width, bmp.height, x, y, state.brushSize, state.brushAdding)
        // Debounced: only refresh overlay if 80 ms of quiet time elapses
        scheduleOverlayRefresh(mask, bmp.width, bmp.height, debounceMs = 80)
    }

    /**
     * Call this on drag END to force the final overlay render regardless of debounce.
     */
    fun commitBrushStroke() {
        val state = _uiState.value
        val mask  = state.mask ?: return
        val bmp   = state.originalBitmap ?: return
        refreshOverlayImmediate(mask, bmp.width, bmp.height)
    }

    fun smartBrush(x: Float, y: Float) {
        val state    = _uiState.value
        val mask     = state.mask ?: return
        val original = state.originalBitmap ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val pixels = IntArray(original.width * original.height)
            original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
            NativeProcessor.smartBrushSelect(
                pixels, mask, original.width, original.height,
                x, y, state.brushSize * 3f, state.smartTolerance, state.brushAdding
            )
            // Snapshot and render overlay on the same background thread
            renderOverlayFromSnapshot(mask.copyOf(), original.width, original.height)
        }
    }

    fun applyRadialMask(cx: Float, cy: Float, inner: Float, outer: Float, inverted: Boolean) {
        val state = _uiState.value
        val bmp   = state.originalBitmap ?: return
        val mask  = ByteArray(bmp.width * bmp.height)
        NativeProcessor.radialMask(mask, bmp.width, bmp.height, cx, cy, inner, outer, inverted)
        _uiState.update { it.copy(mask = mask) }
        refreshOverlayImmediate(mask, bmp.width, bmp.height)
    }

    fun applyLinearMask(sx: Float, sy: Float, ex: Float, ey: Float) {
        val state = _uiState.value
        val bmp   = state.originalBitmap ?: return
        val mask  = ByteArray(bmp.width * bmp.height)
        NativeProcessor.linearGradientMask(mask, bmp.width, bmp.height, sx, sy, ex, ey)
        _uiState.update { it.copy(mask = mask) }
        refreshOverlayImmediate(mask, bmp.width, bmp.height)
    }

    fun applyColorRangeMask(r: Int, g: Int, b: Int) {
        val state    = _uiState.value
        val original = state.originalBitmap ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val mask   = ByteArray(original.width * original.height)
            val pixels = IntArray(original.width * original.height)
            original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
            NativeProcessor.colorRangeMask(pixels, mask, original.width, original.height, r, g, b, state.smartTolerance)
            _uiState.update { it.copy(mask = mask) }
            renderOverlayFromSnapshot(mask.copyOf(), original.width, original.height)
        }
    }

    fun invertMask() {
        val mask = _uiState.value.mask ?: return
        NativeProcessor.invertMask(mask)
        val bmp  = _uiState.value.originalBitmap ?: return
        refreshOverlayImmediate(mask, bmp.width, bmp.height)
    }

    fun clearMask() {
        val mask = _uiState.value.mask ?: return
        NativeProcessor.clearMask(mask, false)
        val bmp  = _uiState.value.originalBitmap ?: return
        refreshOverlayImmediate(mask, bmp.width, bmp.height)
    }

    // ── Overlay rendering helpers ─────────────────────────────────────────

    /**
     * Debounced overlay refresh for rapid brush events.
     * Cancels any pending overlay job; only fires after [debounceMs] of silence.
     * Takes a SNAPSHOT of the mask at the scheduled execution time to avoid
     * read/write races with concurrent paintMask calls.
     */
    private fun scheduleOverlayRefresh(mask: ByteArray, w: Int, h: Int, debounceMs: Long) {
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(debounceMs)
            // Take snapshot AFTER the delay — captures the latest painted state
            val snapshot = mask.copyOf()
            renderOverlayFromSnapshot(snapshot, w, h)
        }
    }

    /**
     * Immediate overlay refresh — cancels debounce, renders right away.
     * Always takes a snapshot to avoid reading a mask that paintMask is writing.
     */
    private fun refreshOverlayImmediate(mask: ByteArray, w: Int, h: Int) {
        overlayJob?.cancel()
        val snapshot = mask.copyOf()
        overlayJob = viewModelScope.launch(Dispatchers.Default) {
            renderOverlayFromSnapshot(snapshot, w, h)
        }
    }

    /**
     * Core render — always receives an immutable snapshot, safe to call from any thread.
     */
    private suspend fun renderOverlayFromSnapshot(snapshot: ByteArray, w: Int, h: Int) {
        withContext(Dispatchers.Default) {
            val pixels  = NativeProcessor.getMaskOverlay(snapshot, w, h)
            val overlay = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
            _uiState.update { cur -> cur.copy(overlayBitmap = overlay) }
        }
    }

    // ── Brush settings ────────────────────────────────────────────────────

    fun setBrushSize(size: Float)       = _uiState.update { it.copy(brushSize     = size) }
    fun setBrushAdding(adding: Boolean) = _uiState.update { it.copy(brushAdding   = adding) }
    fun setMaskMode(mode: MaskMode)     = _uiState.update { it.copy(maskMode      = mode) }
    fun setSmartTolerance(t: Float)     = _uiState.update { it.copy(smartTolerance = t) }
    fun setActiveTab(tab: EditorTab)    = _uiState.update { it.copy(activeTab     = tab) }

    // ── Export ────────────────────────────────────────────────────────────

    fun saveToGallery(context: Context) {
        val display = _uiState.value.displayBitmap ?: return
        _uiState.update { it.copy(isSaving = true, saveError = null, savedPath = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir  = File(context.getExternalFilesDir(null), "ProEdit")
                dir.mkdirs()
                val file = File(dir, "proedit_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    display.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                _uiState.update { it.copy(isSaving = false, savedPath = file.absolutePath) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = e.message) }
            }
        }
    }

    fun resetAdjustments() {
        _uiState.update { it.copy(adjustments = AdjustmentState(), activeFilter = 0) }
        scheduleApply()
    }

    fun clearSaveState() = _uiState.update { it.copy(savedPath = null, saveError = null) }

    override fun onCleared() {
        super.onCleared()
        // Safe to recycle here — ViewModel destroyed, no UI holds references
        _uiState.value.let { s ->
            s.displayBitmap?.recycle()
            s.originalBitmap?.recycle()
            s.overlayBitmap?.recycle()
        }
    }
}
