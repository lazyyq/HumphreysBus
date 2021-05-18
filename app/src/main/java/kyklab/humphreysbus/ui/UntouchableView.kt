package kyklab.humphreysbus.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

open class UntouchableView : View {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes)

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        performClick()
        return false
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}