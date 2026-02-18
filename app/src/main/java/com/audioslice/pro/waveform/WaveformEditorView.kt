package com.audioslice.pro.waveform

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class WaveformEditorView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    interface OnSelectionChangeListener { fun onSelectionChanged(startMs: Long, endMs: Long); fun onPlayheadMoved(positionMs: Long) }
    var selectionListener: OnSelectionChangeListener? = null
    private var waveformData: WaveformData? = null
    private var samples: FloatArray = floatArrayOf()
    private var zoomLevel = 1.0f
    private var scrollOffset = 0f
    private var selectionStart = 0f
    private var selectionEnd = 0f
    private var playheadPosition = 0f
    private var isDraggingStart = false
    private var isDraggingEnd = false
    private var isDraggingPlayhead = false
    private val waveformPaint = Paint().apply { color = Color.parseColor("#E94560"); style = Paint.Style.FILL; isAntiAlias = true }
    private val waveformFillPaint = Paint().apply { color = Color.parseColor("#33E94560"); style = Paint.Style.FILL }
    private val selectionPaint = Paint().apply { color = Color.parseColor("#4DFFFFFF"); style = Paint.Style.FILL }
    private val selectionBorderPaint = Paint().apply { color = Color.parseColor("#FFFFFF"); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val handlePaint = Paint().apply { color = Color.parseColor("#E94560"); style = Paint.Style.FILL }
    private val playheadPaint = Paint().apply { color = Color.parseColor("#FFD700"); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val gridPaint = Paint().apply { color = Color.parseColor("#33FFFFFF"); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val textPaint = Paint().apply { color = Color.parseColor("#99FFFFFF"); textSize = 24f; isAntiAlias = true }

    fun setWaveformData(data: WaveformData) {
        waveformData = data
        samples = data.amplitudes
        zoomLevel = 1.0f
        scrollOffset = 0f
        selectionStart = 0f
        selectionEnd = width.toFloat()
        invalidate()
    }

    fun setPlayheadPosition(ms: Long) {
        val totalMs = waveformData?.durationMs ?: return
        playheadPosition = (ms.toFloat() / totalMs) * getTotalWidth()
        invalidate()
    }

    fun getSelectionRange(): Pair<Long, Long> {
        val totalMs = waveformData?.durationMs ?: return Pair(0, 0)
        val totalWidth = getTotalWidth()
        val startMs = ((selectionStart / totalWidth) * totalMs).toLong()
        val endMs = ((selectionEnd / totalWidth) * totalMs).toLong()
        return Pair(startMs.coerceIn(0, totalMs), endMs.coerceIn(0, totalMs))
    }

    fun zoomIn() { zoomLevel *= 1.5f; constrainScroll(); invalidate() }
    fun zoomOut() { zoomLevel /= 1.5f; zoomLevel = max(1f, zoomLevel); constrainScroll(); invalidate() }
    fun resetZoom() { zoomLevel = 1f; scrollOffset = 0f; invalidate() }
    fun selectAll() { selectionStart = 0f; selectionEnd = getTotalWidth(); invalidate(); notifySelectionChanged() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (selectionEnd == 0f || selectionEnd > getTotalWidth()) selectionEnd = getTotalWidth()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#1A1A2E"))
        if (samples.isEmpty()) { canvas.drawText("No audio loaded", width / 2f - 100f, height / 2f, textPaint); return }
        val totalWidth = getTotalWidth()
        val visibleStart = scrollOffset
        val visibleEnd = scrollOffset + width
        drawGrid(canvas, totalWidth)
        drawWaveform(canvas, visibleStart, visibleEnd)
        drawSelection(canvas)
        drawPlayhead(canvas)
        drawTimeMarkers(canvas, totalWidth)
    }

    private fun drawGrid(canvas: Canvas, totalWidth: Float) {
        val majorStep = totalWidth / 10
        val minorStep = majorStep / 4
        for (x in 0..(totalWidth / minorStep).toInt()) {
            val pos = x * minorStep - scrollOffset
            if (pos in 0f..width.toFloat()) canvas.drawLine(pos, 0f, pos, height.toFloat(), if (x % 4 == 0) gridPaint else gridPaint.apply { alpha = 50 })
        }
        canvas.drawLine(0f, height * 0.75f, width.toFloat(), height * 0.75f, gridPaint)
    }

    private fun drawWaveform(canvas: Canvas, visibleStart: Float, visibleEnd: Float) {
        val path = Path()
        val centerY = height * 0.75f
        val maxAmplitude = height * 0.25f
        val sampleWidth = getTotalWidth() / samples.size
        val startSample = (visibleStart / sampleWidth).toInt().coerceIn(0, samples.size - 1)
        val endSample = (visibleEnd / sampleWidth).toInt().coerceIn(0, samples.size - 1)
        if (startSample >= endSample) return
        path.moveTo(0f, centerY)
        for (i in startSample..endSample) {
            val x = (i * sampleWidth) - scrollOffset
            val amplitude = samples[i] * maxAmplitude
            path.lineTo(x, centerY - amplitude)
        }
        for (i in endSample downTo startSample) {
            val x = (i * sampleWidth) - scrollOffset
            val amplitude = samples[i] * maxAmplitude
            path.lineTo(x, centerY + amplitude)
        }
        path.close()
        canvas.drawPath(path, waveformFillPaint)
        val outlinePath = Path()
        for (i in startSample..endSample) {
            val x = (i * sampleWidth) - scrollOffset
            val amplitude = samples[i] * maxAmplitude
            if (i == startSample) outlinePath.moveTo(x, centerY - amplitude) else outlinePath.lineTo(x, centerY - amplitude)
        }
        canvas.drawPath(outlinePath, waveformPaint)
    }

    private fun drawSelection(canvas: Canvas) {
        if (selectionStart >= selectionEnd) return
        val left = selectionStart - scrollOffset
        val right = selectionEnd - scrollOffset
        canvas.drawRect(left, 0f, right, height.toFloat(), selectionPaint)
        canvas.drawLine(left, 0f, left, height.toFloat(), selectionBorderPaint)
        canvas.drawLine(right, 0f, right, height.toFloat(), selectionBorderPaint)
        val handleSize = 20f
        canvas.drawRect(left - handleSize, height / 2f - handleSize, left + handleSize, height / 2f + handleSize, handlePaint)
        canvas.drawRect(right - handleSize, height / 2f - handleSize, right + handleSize, height / 2f + handleSize, handlePaint)
    }

    private fun drawPlayhead(canvas: Canvas) {
        val x = playheadPosition - scrollOffset
        if (x < 0 || x > width) return
        canvas.drawLine(x, 0f, x, height.toFloat(), playheadPaint)
        val trianglePath = Path()
        trianglePath.moveTo(x, 0f)
        trianglePath.lineTo(x - 10, 15f)
        trianglePath.lineTo(x + 10, 15f)
        trianglePath.close()
        canvas.drawPath(trianglePath, playheadPaint.apply { style = Paint.Style.FILL })
    }

    private fun drawTimeMarkers(canvas: Canvas, totalWidth: Float) {
        val totalMs = waveformData?.durationMs ?: return
        val intervalMs = when { totalMs < 60000 -> 5000L; totalMs < 300000 -> 30000L; totalMs < 600000 -> 60000L; else -> 300000L }
        var currentMs = 0L
        while (currentMs <= totalMs) {
            val x = (currentMs.toFloat() / totalMs) * totalWidth - scrollOffset
            if (x in 0f..width.toFloat()) canvas.drawText(formatTime(currentMs), x + 5, height - 10, textPaint)
            currentMs += intervalMs
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x + scrollOffset
                val handleSize = 30f
                isDraggingStart = abs(touchX - selectionStart) < handleSize
                isDraggingEnd = abs(touchX - selectionEnd) < handleSize
                isDraggingPlayhead = abs(touchX - playheadPosition) < handleSize
                if (!isDraggingStart && !isDraggingEnd && !isDraggingPlayhead) { selectionStart = touchX; selectionEnd = touchX }
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                val touchX = event.x + scrollOffset
                when {
                    isDraggingStart -> { selectionStart = touchX.coerceIn(0f, selectionEnd - 10f); notifySelectionChanged() }
                    isDraggingEnd -> { selectionEnd = touchX.coerceIn(selectionStart + 10f, getTotalWidth()); notifySelectionChanged() }
                    isDraggingPlayhead -> { playheadPosition = touchX.coerceIn(0f, getTotalWidth()); notifyPlayheadMoved() }
                    else -> { selectionEnd = touchX.coerceIn(0f, getTotalWidth()); if (selectionEnd < selectionStart) { val temp = selectionStart; selectionStart = selectionEnd; selectionEnd = temp } }
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> { isDraggingStart = false; isDraggingEnd = false; isDraggingPlayhead = false; notifySelectionChanged(); return true }
        }
        return super.onTouchEvent(event)
    }

    private fun getTotalWidth(): Float = width * zoomLevel
    private fun constrainScroll() { val maxScroll = max(0f, getTotalWidth() - width); scrollOffset = scrollOffset.coerceIn(0f, maxScroll) }
    private fun notifySelectionChanged() { val (startMs, endMs) = getSelectionRange(); selectionListener?.onSelectionChanged(startMs, endMs) }
    private fun notifyPlayheadMoved() { val totalMs = waveformData?.durationMs ?: return; val ms = ((playheadPosition / getTotalWidth()) * totalMs).toLong(); selectionListener?.onPlayheadMoved(ms) }
    private fun formatTime(ms: Long): String { val seconds = ms / 1000; val minutes = seconds / 60; val hours = minutes / 60; return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60) else String.format("%d:%02d", minutes, seconds % 60) }
}

data class WaveformData(val amplitudes: FloatArray, val durationMs: Long, val sampleRate: Int) {
    override fun equals(other: Any?): Boolean { if (this === other) return true; if (javaClass != other?.javaClass) return false; other as WaveformData; if (!amplitudes.contentEquals(other.amplitudes)) return false; if (durationMs != other.durationMs) return false; if (sampleRate != other.sampleRate) return false; return true }
    override fun hashCode(): Int { var result = amplitudes.contentHashCode(); result = 31 * result + durationMs.hashCode(); result = 31 * result + sampleRate; return result }
}
