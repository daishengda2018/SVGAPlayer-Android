package com.opensource.svgaplayer

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.opensource.svgaplayer.utils.SVGARange
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.net.URL

/**
 * Created by PonyCui on 2017/3/29.
 */
@Suppress("NewApi")
open class SVGAImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0)
    : ImageView(context, attrs, defStyleAttr, defStyleRes) {

    enum class FillMode {
        Backward,
        Forward,
    }

    var isAnimating = false
        private set

    var loops = 0
    var clearsAfterStop = true
    var fillMode: FillMode = FillMode.Forward
    var callback: SVGACallback? = null

    private var mAnimator: ValueAnimator? = null
    private var mItemClickAreaListener: SVGAClickAreaListener? = null
    private var mAntiAlias = true
    private var mAutoPlay = true
    private val mAnimatorListener = AnimatorListener(this)
    private val mAnimatorUpdateListener = AnimatorUpdateListener(this)
    private var mStartFrame = 0
    private var mEndFrame = 0

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            this.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        attrs?.let { loadAttrs(it) }
    }

    private fun loadAttrs(attrs: AttributeSet) {
        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.SVGAImageView, 0, 0)
        loops = typedArray.getInt(R.styleable.SVGAImageView_loopCount, 0)
        clearsAfterStop = typedArray.getBoolean(R.styleable.SVGAImageView_clearsAfterStop, true)
        mAntiAlias = typedArray.getBoolean(R.styleable.SVGAImageView_antiAlias, true)
        mAutoPlay = typedArray.getBoolean(R.styleable.SVGAImageView_autoPlay, true)
        typedArray.getString(R.styleable.SVGAImageView_fillMode)?.let {
            if (it == "0") {
                fillMode = FillMode.Backward
            } else if (it == "1") {
                fillMode = FillMode.Forward
            }
        }
        typedArray.getString(R.styleable.SVGAImageView_source)?.let {
            parserSource(it)
        }
        typedArray.recycle()
    }

    private fun parserSource(source: String) {
        // 使用弱引用解决内存泄漏
        val ref = WeakReference<SVGAImageView>(this)

        // 在后台启动一个新的协程并继续
        GlobalScope.launch {
            val parser = SVGAParser(context)
            if (source.startsWith("http://") || source.startsWith("https://")) {
                parser.decodeFromURL(URL(source), createParseCompletion(ref))
            } else {
                parser.decodeFromAssets(source, createParseCompletion(ref))
            }
        }
    }

    private fun createParseCompletion(ref: WeakReference<SVGAImageView>): SVGAParser.ParseCompletion {
        return object : SVGAParser.ParseCompletion {
            override fun onComplete(videoItem: SVGAVideoEntity) {
                ref.get()?.startAnimation(videoItem)
            }

            override fun onError() {}
        }
    }

    private fun startAnimation(videoItem: SVGAVideoEntity) {
        this@SVGAImageView.post {
            videoItem.antiAlias = mAntiAlias
            setVideoItem(videoItem)
            getSVGADrawable()?.scaleType = scaleType
            if (mAutoPlay) {
                startAnimation()
            }
        }
    }

    fun startAnimation() {
        startAnimation(null, false)
    }

    fun startAnimation(range: SVGARange?, reverse: Boolean = false) {
        stopAnimation(false)
        play(range, reverse)
    }

    private fun play(range: SVGARange?, reverse: Boolean) {
        val drawable = getSVGADrawable() ?: return
        setupDrawable()
        mStartFrame = Math.max(0, range?.location ?: 0)
        val videoItem = drawable.videoItem
        mEndFrame = Math.min(videoItem.frames - 1, ((range?.location ?: 0) + (range?.length ?: Int.MAX_VALUE) - 1))
        val animator = ValueAnimator.ofInt(mStartFrame, mEndFrame)
        animator.interpolator = LinearInterpolator()
        animator.duration = ((mEndFrame - mStartFrame + 1) * (1000 / videoItem.FPS) / generateScale()).toLong()
        animator.repeatCount = if (loops <= 0) 99999 else loops - 1
        animator.addUpdateListener(mAnimatorUpdateListener)
        animator.addListener(mAnimatorListener)
        if (reverse) {
            animator.reverse()
        } else {
            animator.start()
        }
        mAnimator = animator
    }

    private fun setupDrawable() {
        val drawable = getSVGADrawable() ?: return
        drawable.cleared = false
        drawable.scaleType = scaleType
        drawable.videoItem.init(width, height)
        Log.d("SVGAImageView", "height:$height width:$width")
    }

    private fun getSVGADrawable(): SVGADrawable? {
        return drawable as? SVGADrawable
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    private fun generateScale(): Double {
        var scale = 1.0
        try {
            val animatorClass = Class.forName("android.animation.ValueAnimator") ?: return scale
            val field = animatorClass.getDeclaredField("sDurationScale") ?: return scale
            field.isAccessible = true
            scale = field.getFloat(animatorClass).toDouble()
            if (scale == 0.0) {
                field.setFloat(animatorClass, 1.0f)
                scale = 1.0
                Log.e("SVGAPlayer", "The animation duration scale has been reset to 1.0x," +
                        " because you closed it on developer options.")
            }
        } catch (ignore: Exception) {
        }
        return scale
    }

    private fun onAnimatorUpdate(animator: ValueAnimator?) {
        val drawable = getSVGADrawable() ?: return
        drawable.currentFrame = animator?.animatedValue as Int
        val percentage = (drawable.currentFrame + 1).toDouble() / drawable.videoItem.frames.toDouble()
        callback?.onStep(drawable.currentFrame, percentage)
    }

    private fun onAnimationEnd(animation: Animator?) {
        isAnimating = false
        val drawable = getSVGADrawable()
        if (!clearsAfterStop && drawable != null) {
            if (fillMode == FillMode.Backward) {
                drawable.currentFrame = mStartFrame
            } else if (fillMode == FillMode.Forward) {
                drawable.currentFrame = mEndFrame
            }
        }

        callback?.onFinished()
        // 不是循环播放时，手动停止一下
        if ((animation as ValueAnimator).repeatCount <= 0) {
            Log.d("## onAnimationEnd", "onFinished")
            // 要根据用户设置的 clearsAfterStop 状态判断，不可手动置 true
            stopAnimation()
        }
    }

    fun pauseAnimation() {
        Log.d("## pauseAnimation", "")
        stopAnimation(false)
        callback?.onPause()
    }

    fun stopAnimation() {
        stopAnimation(clear = clearsAfterStop)
    }

    fun stopAnimation(clear: Boolean) {
        Log.d("## stopAnimation", "cancel")

        mAnimator?.cancel()
        mAnimator?.removeAllListeners()
        mAnimator?.removeAllUpdateListeners()
        if (clear) {
            getSVGADrawable()?.cleared = true
            // 回收内存
            getSVGADrawable()?.release()
            // 清除对 drawable 的引用
            setImageDrawable(null)
        }
        getSVGADrawable()?.clearAudio()
    }

    fun setVideoItem(videoItem: SVGAVideoEntity?) {
        setVideoItem(videoItem, SVGADynamicEntity())
    }

    fun setVideoItem(videoItem: SVGAVideoEntity?, dynamicItem: SVGADynamicEntity = SVGADynamicEntity()) {
        if (videoItem == null) {
            setImageDrawable(null)
        } else {
            val drawable = SVGADrawable(videoItem, dynamicItem)
            drawable.cleared = clearsAfterStop
            setImageDrawable(drawable)
        }
    }

    fun stepToFrame(frame: Int, andPlay: Boolean) {
        pauseAnimation()
        val drawable = getSVGADrawable() ?: return
        drawable.currentFrame = frame
        if (andPlay) {
            startAnimation()
            mAnimator?.let {
                it.currentPlayTime = (Math.max(0.0f, Math.min(1.0f, (frame.toFloat() / drawable.videoItem.frames.toFloat()))) * it.duration).toLong()
            }
        }
    }

    fun stepToPercentage(percentage: Double, andPlay: Boolean) {
        val drawable = drawable as? SVGADrawable ?: return
        var frame = (drawable.videoItem.frames * percentage).toInt()
        if (frame >= drawable.videoItem.frames && frame > 0) {
            frame = drawable.videoItem.frames - 1
        }
        stepToFrame(frame, andPlay)
    }

    fun setOnAnimKeyClickListener(clickListener : SVGAClickAreaListener){
        mItemClickAreaListener = clickListener
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event)
        }
        val drawable = getSVGADrawable() ?: return false
        for ((key, value) in drawable.dynamicItem.mClickMap) {
            if (event.x >= value[0] && event.x <= value[2] && event.y >= value[1] && event.y <= value[3]) {
                mItemClickAreaListener?.let {
                    it.onClick(key)
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation(true)
    }

    private class AnimatorListener(view: SVGAImageView) : Animator.AnimatorListener {
        private val weakReference = WeakReference<SVGAImageView>(view)

        override fun onAnimationRepeat(animation: Animator?) {
            weakReference.get()?.callback?.onRepeat()
            Log.d("## onAnimationRepeat", (animation as ValueAnimator).repeatCount.toString())
        }

        override fun onAnimationEnd(animation: Animator?) {
            Log.d("## onAnimationEnd", (animation as ValueAnimator).repeatCount.toString())
            weakReference.get()?.onAnimationEnd(animation)
        }

        override fun onAnimationCancel(animation: Animator?) {
            Log.d("## onAnimationCancel", (animation as ValueAnimator).duration.toString())
            weakReference.get()?.isAnimating = false
        }

        override fun onAnimationStart(animation: Animator?) {
            Log.d("## onAnimationStart", (animation as ValueAnimator).duration.toString())
            weakReference.get()?.isAnimating = true
        }
    } // end of AnimatorListener


    private class AnimatorUpdateListener(view: SVGAImageView) : ValueAnimator.AnimatorUpdateListener {
        private val weakReference = WeakReference<SVGAImageView>(view)

        override fun onAnimationUpdate(animation: ValueAnimator?) {
            Log.d("## onAnimationUpdate", (animation as ValueAnimator).repeatCount.toString())

            weakReference.get()?.onAnimatorUpdate(animation)
        }
    } // end of AnimatorUpdateListener
}