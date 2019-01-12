package xyz.mcomella.customdrawing

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.res.ResourcesCompat
import android.util.AttributeSet
import android.util.Log
import android.view.View

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}

class ShadowView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    val shadowHeight = resources.dpToPx(6f) // todo: 6f
    val circleRadius = resources.getDimension(R.dimen.corner_radius)

    val shadowColor: Int = (0x28000000).toInt() // not linear gradient
//    val shadowColor: Int = (0xFF000000).toInt() // linear gradient

    val shadowGradient = LinearGradient(0f, circleRadius, 0f, 0f, shadowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    lateinit var topLeftGradient: RadialGradient
    lateinit var topRightGradient: RadialGradient

    val highShadGrad = LinearGradient(0f, shadowHeight, 0f, 0f, shadowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    lateinit var highShadTL: RadialGradient
    lateinit var highShadTR: RadialGradient

    val foregroundPath = Path()

    val paint = Paint().apply {
    }

    val foregroundPaint = Paint().apply {
        isAntiAlias = true
        color = (0xFF38383D).toInt()
    }

    // todo: explain why right, bottom size
    val foregroundTopRect = RectF(circleRadius, 0f, 0f, 0f)
    val foregroundBottomRect = RectF(0f, circleRadius, 0f, 0f)

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        val widthFloat = width.toFloat()
        val heightFloat = height.toFloat()

        topLeftGradient = RadialGradient(circleRadius, circleRadius, circleRadius, shadowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        topRightGradient = RadialGradient(widthFloat - circleRadius, circleRadius, circleRadius, shadowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)

        foregroundTopRect.bottom = circleRadius
        foregroundTopRect.right = widthFloat - circleRadius

        foregroundBottomRect.bottom = heightFloat
        foregroundBottomRect.right = widthFloat

        fun setForegroundPath(): Unit = with(foregroundPath) {
            reset()

            addRect(foregroundTopRect, Path.Direction.CW)
            addRect(foregroundBottomRect, Path.Direction.CW)

            addCircle(circleRadius, circleRadius, circleRadius, Path.Direction.CW) // Top left corner
            addCircle(width - circleRadius, circleRadius, circleRadius, Path.Direction.CW) // Top right corner
        }

        fun setShadowArcPath(): Unit = with(shadowArcPath) {
            reset()

            moveTo(0f, circleRadius + shadowHeight)
            lineTo(0f, circleRadius)
            arcTo(0f, 0f, circleRadius * 2, circleRadius * 2, 180f, 90f, false)
            lineTo(widthFloat - circleRadius, 0f)
            arcTo(widthFloat - circleRadius * 2, 0f, widthFloat, circleRadius * 2, 270f, 90f, false)
            lineTo(widthFloat, circleRadius + shadowHeight)
        }

        setForegroundPath()
        setShadowArcPath()
    }

    val shadowArcPath = Path()
    val shadowArcPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        strokeWidth = resources.dpToPx(1f)
        style = Paint.Style.STROKE
    }

    // todo:
    // - verify performance
    // - getting shadow right: shadow begins at circleRadius rather than edge of foreground view. Maybe this is good enough for now?
    // - How does AOSP do it?
    // - Alternative: Draw arcs with canvas translation or pixel-by-pixel calculate distance from radius. Cache in bitmap?
    // -
    override fun onDraw(canvas: Canvas) {
        // Problem: shadow gradient starts at arbitrary point so hard to define desirable shadow.
        // But can we push linear gradient up higher?
        fun drawShadowLinearGradient(): Unit = canvas.restoreAfter {
            paint.shader = shadowGradient
            canvas.drawRect(foregroundTopRect, paint)

            // todo: use cached box for this? cache circle radius?
            // Need to extend background to
            paint.shader = topLeftGradient
            canvas.drawArc(0f, 0f, circleRadius * 2, circleRadius * 2, 180f, 90f, true, paint)
            paint.shader = topRightGradient
            canvas.drawArc(width - circleRadius * 2, 0f, width.toFloat(), circleRadius * 2, 270f, 90f, true, paint)
        }

        fun drawShadowLinearGradientHigher(): Unit = canvas.restoreAfter {
            paint.shader = highShadGrad
            canvas.drawRect(circleRadius, 0f, circleRadius, shadowHeight, paint)

            canvas.restoreAfter {
                // todo: clip arc, draw gradient on arc (what point?), will extend in wrong direction
//                canvas.drawArc()
            }
        }

        fun drawShadowArcs() {
            for (i in 0 until shadowHeight.toInt()) {
                val shadowPercent = i / (shadowHeight - 1)
                // strangely using unrelated shadowColor here.
                shadowArcPaint.alpha = Math.floor(Color.alpha(shadowColor) * shadowPercent.toDouble()).toInt()
                canvas.restoreAfter {
                    canvas.translate(0f, i.toFloat())
                    val scaleX = 1f + (shadowHeight - 1 - i) * 0.00075f
                    canvas.scale(scaleX, 1f, width / 2f, 0f)
                    canvas.drawPath(shadowArcPath, shadowArcPaint)
                }
            }
        }

        fun drawShadowArcsNoAlpha() {
            val shadowStart: Int = (0xFF28282B).toInt()
            val shadowEnd: Int = (0xFF38383D).toInt()
            val redDistance = Math.abs(Color.red(shadowStart) - Color.red(shadowEnd))
            val blueDistance = Math.abs(Color.blue(shadowStart) - Color.blue(shadowEnd))
            val greenDistance = Math.abs(Color.green(shadowStart) - Color.green(shadowEnd))

            for (i in 0 until shadowHeight.toInt()) {
                val shadowPercent = i / (shadowHeight - 1)

                val red = Color.red(shadowEnd) - redDistance * shadowPercent
                val green = Color.green(shadowEnd) - greenDistance * shadowPercent
                val blue = Color.blue(shadowEnd) - blueDistance * shadowPercent
                shadowArcPaint.color = Color.rgb(red.toInt(), green.toInt(), blue.toInt())

                canvas.restoreAfter {
                    canvas.translate(0f, i.toFloat())
                    canvas.drawPath(shadowArcPath, shadowArcPaint)
                }
            }
        }


        fun drawForeground(): Unit = canvas.restoreAfter {
            canvas.translate(0f, shadowHeight)
            canvas.drawPath(foregroundPath, foregroundPaint)
        }

        super.onDraw(canvas)

//        drawShadowLinearGradient()
        drawShadowArcs()
        drawForeground() // todo: it's the same whether or not we draw this.
    }
}

fun Resources.dpToPx(dp: Float): Float = displayMetrics.density * dp

inline fun Canvas.restoreAfter(block: (canvas: Canvas) -> Unit) {
    save()
    block(this)
    restore()
}
