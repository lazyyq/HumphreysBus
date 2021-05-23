package kyklab.humphreysbus.utils

import android.icu.util.Calendar
import android.util.Log
import java.util.*

class MinDateTime {
    /**
     * 4 digits, 1 <= yy
     */
    var yy: String
        set(value) {
            if (value.length == 4) field = value
            else throw WrongParameterException()
        }

    /**
     * 2 digits, 1 <= mm <= 12
     */
    var mm: String
        set(value) {
            if (value.length == 2) field = value
            else throw WrongParameterException()
        }

    /**
     * 2 digits, 1 <= dd <= 31
     */
    var dd: String
        set(value) {
            if (value.length == 2) field = value
            else throw WrongParameterException()
        }

    /**
     * 2 digits, 0 <= h <= 23
     */
    var h: String
        set(value) {
            if (value.length == 2) field = value
            else throw WrongParameterException()
        }

    /**
     * 2 digits, 0 <= m <= 59
     */
    var m: String
        set(value) {
            if (value.length == 2) field = value
            else throw WrongParameterException()
        }

    /**
     * 2 digits, 0 <= s <= 59
     */
    var s: String
        set(value) {
            if (value.length == 2) field = value
            else throw WrongParameterException()
        }

    constructor() : this(YY_MIN_S, MM_MIN_S, DD_MIN_S, H_MIN_S, M_MIN_S, S_MIN_S)

    constructor(clone: MinDateTime) :
            this(clone.yy, clone.mm, clone.dd, clone.h, clone.m, clone.s)

    constructor(
        h: String = H_MIN_S,
        m: String = M_MIN_S,
        s: String = S_MIN_S
    ) : this(
        yy = YY_MIN_S,
        mm = MM_MIN_S,
        dd = DD_MIN_S,
        h = h, m = m, s = s
    )

    constructor(
        yy: String = YY_MIN_S,
        mm: String = MM_MIN_S,
        dd: String = DD_MIN_S,
        h: String = H_MIN_S,
        m: String = M_MIN_S,
        s: String = S_MIN_S
    ) {
        this.yy = yy; this.mm = mm; this.dd = dd
        this.h = h; this.m = m; this.s = s
    }

    constructor(
        h: Int = H_MIN, m: Int = M_MIN, s: Int = S_MIN
    ) : this(
        yy = YY_MIN, mm = MM_MIN, dd = DD_MIN,
        h = h, m = m, s = s
    )

    constructor(
        yy: Int = YY_MIN, mm: Int = MM_MIN, dd: Int = DD_MIN,
        h: Int = H_MIN, m: Int = M_MIN, s: Int = S_MIN
    ) {
        if (
            yy < YY_MIN ||
            mm !in MM_MIN..MM_MAX ||
            dd !in DD_MIN..DD_MAX ||
            h !in H_MIN..H_MAX ||
            m !in M_MIN..M_MAX ||
            s !in S_MIN..S_MAX
        ) {
            throw WrongParameterException()
        } else {
            this.yy = yy.fourDigits()
            this.mm = mm.twoDigits()
            this.dd = dd.twoDigits()
            this.h = h.twoDigits()
            this.m = m.twoDigits()
            this.s = s.twoDigits()
        }
    }

    /**
     * Special constructor without range check
     * Only for use in plus or minus operator
     */
    private constructor(
        yy: Int = YY_MIN, mm: Int = MM_MIN, dd: Int = DD_MIN,
        h: Int = H_MIN, m: Int = M_MIN, s: Int = S_MIN,
        allowDangerous: Boolean
    ) {
        if (
            yy < YY_MIN ||
            mm !in MM_MIN..MM_MAX ||
            dd !in DD_MIN..DD_MAX ||
            h !in H_MIN..H_MAX ||
            m !in M_MIN..M_MAX ||
            s !in S_MIN..S_MAX
        ) {
            Log.w(TAG, "Instance without range check created: $this")
        }

        var tmpYy = yy.fourDigits()
        if (tmpYy.length > 4) tmpYy = tmpYy.substring(tmpYy.length - 4 until tmpYy.length)
        this.yy = tmpYy
        this.mm = mm.twoDigits()
        this.dd = dd.twoDigits()
        this.h = h.twoDigits()
        this.m = m.twoDigits()
        this.s = s.twoDigits()
    }

    class WrongParameterException : Exception()

    var yymmdd: String
        get() = yy + mm + dd
        set(value) {
            value.assertLength(6)
            yy = value.substring(0..1)
            mm = value.substring(2..3)
            dd = value.substring(4..5)
        }

    var hm: String
        get() = h + m
        set(value) {
            value.assertLength(4)
            h = value.substring(0..1)
            m = value.substring(2..3)
        }

    var ms: String
        get() = m + s
        set(value) {
            value.assertLength(4)
            m = value.substring(0..1)
            s = value.substring(2..3)
        }

    var hms: String
        get() = h + m + s
        set(value) {
            value.assertLength(6)
            h = value.substring(0..1)
            m = value.substring(2..3)
            s = value.substring(4..5)
        }

    val h_m: String
        get() = "$h:$m"

    val m_s: String
        get() = "$m:$s"

    val h_m_s: String
        get() = "$h:$m:$s"

    var yymmddhms: String
        get() = yymmdd + hms
        set(value) {
            value.assertLength(12)
            yymmdd = value.substring(0..5)
            hms = value.substring(6..11)
        }

    operator fun plus(value: MinDateTime): MinDateTime {
        var carry: Int

        var newS = this.s.toInt() + value.s.toInt()
        if (newS > S_MAX) {
            newS -= (S_MAX - S_MIN + 1)
            carry = 1
        } else carry = 0

        var newM = this.m.toInt() + value.m.toInt() + carry
        if (newM > M_MAX) {
            newM -= (M_MAX - M_MIN + 1)
            carry = 1
        } else carry = 0

        var newH = this.h.toInt() + value.h.toInt() + carry
        if (newH > H_MAX) {
            newH -= (H_MAX - H_MIN + 1)
            carry = 1
        } else carry = 0

        var newDD = this.dd.toInt() + value.dd.toInt() + carry
        if (newDD > DD_MAX) {
            newDD -= (DD_MAX - DD_MIN + 1)
            carry = 1
        } else carry = 0

        var newMM = this.mm.toInt() + value.mm.toInt() + carry
        if (newMM > MM_MAX) {
            newMM -= (MM_MAX - MM_MIN + 1)
            carry = 1
        } else carry = 0

        val newYY = this.yy.toInt() + value.yy.toInt() + carry

        return MinDateTime(newYY, newMM, newDD, newH, newM, newS, true)
    }

    // TODO: Improve
    operator fun plus(value: Int): MinDateTime {
        return MinDateTime(this) + MinDateTime(0, 0, 0, 0, 0, value, true)
    }

    operator fun plusAssign(value: MinDateTime) {
        var carry: Int

        var newS = this.s.toInt() + value.s.toInt()
        if (newS > S_MAX) {
            newS -= (S_MAX - S_MIN + 1)
            carry = 1
        } else carry = 0

        var newM = this.m.toInt() + value.m.toInt() + carry
        if (newM > M_MAX) {
            newM -= (M_MAX - M_MIN + 1)
            carry = 1
        } else carry = 0

        var newH = this.h.toInt() + value.h.toInt() + carry
        if (newH > H_MAX) {
            newH -= (H_MAX - H_MIN + 1)
            carry = 1
        } else carry = 0

        var newDD = this.dd.toInt() + value.dd.toInt() + carry
        if (newDD > DD_MAX) {
            newDD -= (DD_MAX - DD_MIN + 1)
            carry = 1
        } else carry = 0

        var newMM = this.mm.toInt() + value.mm.toInt() + carry
        if (newMM > MM_MAX) {
            newMM -= (MM_MAX - MM_MIN + 1)
            carry = 1
        } else carry = 0

        val newYY = this.yy.toInt() + value.yy.toInt() + carry

        this.yy = newYY.toString()
        this.mm = newMM.toString()
        this.dd = newDD.toString()
        this.h = newH.toString()
        this.m = newM.toString()
        this.s = newS.toString()
    }

    operator fun plusAssign(value: Int) {
        this += MinDateTime(0, 0, 0, 0, 0, value, true)
    }

    operator fun minus(value: MinDateTime): MinDateTime {
        var carry: Int

        var newS = this.s.toInt() - value.s.toInt()
        if (newS < S_MIN) {
            newS += (S_MAX - S_MIN + 1)
            carry = 1
        } else carry = 0

        var newM = this.m.toInt() - value.m.toInt() - carry
        if (newM < M_MIN) {
            newM += (M_MAX - M_MIN + 1)
            carry = 1
        } else carry = 0

        var newH = this.h.toInt() - value.h.toInt() - carry
        if (newH < H_MIN) {
            newH += (H_MAX - H_MIN + 1)
            carry = 1
        } else carry = 0

        var newDD = this.dd.toInt() - value.dd.toInt() - carry
        if (newDD < DD_MIN) {
            newDD += (DD_MAX - DD_MIN + 1)
            carry = 1
        } else carry = 0

        var newMM = this.mm.toInt() - value.mm.toInt() - carry
        if (newMM < MM_MIN) {
            newMM += (MM_MAX - MM_MIN + 1)
            carry = 1
        } else carry = 0

        val newYY = this.yy.toInt() - value.yy.toInt() - carry

        return MinDateTime(newYY, newMM, newDD, newH, newM, newS, true)
    }

    // TODO: Improve
    operator fun minus(value: Int): MinDateTime {
        return MinDateTime(this) - MinDateTime(0, 0, 0, 0, 0, value, true)
    }

    operator fun minusAssign(value: MinDateTime) {
        var carry: Int

        var newS = this.s.toInt() - value.s.toInt()
        if (newS < S_MIN) {
            newS += (S_MAX - S_MIN + 1)
            carry = 1
        } else carry = 0

        var newM = this.m.toInt() - value.m.toInt() - carry
        if (newM < M_MIN) {
            newM += (M_MAX - M_MIN + 1)
            carry = 1
        } else carry = 0

        var newH = this.h.toInt() - value.h.toInt() - carry
        if (newH < H_MIN) {
            newH += (H_MAX - H_MIN + 1)
            carry = 1
        } else carry = 0

        var newDD = this.dd.toInt() - value.dd.toInt() - carry
        if (newDD < DD_MIN) {
            newDD += (DD_MAX - DD_MIN + 1)
            carry = 1
        } else carry = 0

        var newMM = this.mm.toInt() - value.mm.toInt() - carry
        if (newMM < MM_MIN) {
            newMM += (MM_MAX - MM_MIN + 1)
            carry = 1
        } else carry = 0

        val newYY = this.yy.toInt() - value.yy.toInt() - carry

        this.yy = newYY.toString()
        this.mm = newMM.toString()
        this.dd = newDD.toString()
        this.h = newH.toString()
        this.m = newM.toString()
        this.s = newS.toString()
    }

    operator fun minusAssign(value: Int) {
        this -= MinDateTime(0, 0, 0, 0, 0, value, true)
    }

    operator fun compareTo(value: MinDateTime): Int {
        return this.compare(value, true)
    }

    override fun toString() = "$yy/$mm/$dd $h:$m:$s"

    companion object {
        private val TAG = this::class.java.simpleName

        const val YY_MIN = 1
        const val MM_MIN = 1
        const val MM_MAX = 12
        const val DD_MIN = 1
        const val DD_MAX = 31
        const val H_MIN = 0
        const val H_MAX = 23
        const val M_MIN = 0
        const val M_MAX = 59
        const val S_MIN = 0
        const val S_MAX = 59

        const val YY_MIN_S = "0001"
        const val MM_MIN_S = "01"
        const val MM_MAX_S = "12"
        const val DD_MIN_S = "01"
        const val DD_MAX_S = "31"
        const val H_MIN_S = "00"
        const val H_MAX_S = "23"
        const val M_MIN_S = "00"
        const val M_MAX_S = "59"
        const val S_MIN_S = "00"
        const val S_MAX_S = "59"

        private fun Int.format(format: String) = String.format(format, this)
        private fun Int.twoDigits() = format("%02d")
        private fun Int.fourDigits() = format("%04d")
        private fun CharSequence.assertLength(length: Int) {
            if (this.length != length) {
                throw WrongParameterException()
            }
        }

        fun getCurDateTime(): MinDateTime {
            val c = Calendar.getInstance().apply { time = Date() }
            return MinDateTime(
                yy = c.get(Calendar.YEAR).fourDigits(),
                mm = (c.get(Calendar.MONTH) + 1).twoDigits(),
                dd = c.get(Calendar.DAY_OF_MONTH).twoDigits(),
                h = c.get(Calendar.HOUR_OF_DAY).twoDigits(),
                m = c.get(Calendar.MINUTE).twoDigits(),
                s = c.get(Calendar.SECOND).twoDigits()
            )
        }

        fun MinDateTime.setCalendar(calendar: Calendar) {
            yy = calendar.get(Calendar.YEAR).fourDigits()
            mm = (calendar.get(Calendar.MONTH) + 1).twoDigits()
            dd = calendar.get(Calendar.DAY_OF_MONTH).twoDigits()
            h = calendar.get(Calendar.HOUR_OF_DAY).twoDigits()
            m = calendar.get(Calendar.MINUTE).twoDigits()
            s = calendar.get(Calendar.SECOND).twoDigits()
        }

        val MinDateTime.timeInSecs: Int
            get() = h.toInt() * 3600 + m.toInt() * 60 + s.toInt()

        val MinDateTime.timeInMillis: Long
            get() = timeInSecs * 1000L


        fun MinDateTime.compare(other: MinDateTime, considerDates: Boolean): Int {
            val a: String
            val b: String
            if (considerDates) {
                a = "$yy$mm$dd$h$m$s"
                b = with(other) { "$yy$mm$dd$h$m$s" } // TODO: Test
            } else {
                a = "$h$m$s"
                b = with(other) { "$h$m$s" }
            }
            return a.toInt().compareTo(b.toInt())
        }

        /**
         * Check if the given date and time is between `prev` and `next`.
         */
        fun MinDateTime.isBetween(
            prev: MinDateTime,
            next: MinDateTime,
            prevInclusive: Boolean = true,
            considerDates: Boolean = false
        ) =
            if (considerDates) {
                if (prev == this && this == next) {
                    true
                } else if (prevInclusive) {
                    prev <= this && this < next ||
                            prev >= this && this > next
                } else {
                    prev < this && this <= next ||
                            prev > this && this >= next
                }
            } else {
                if (prev.compare(this, false) == 0 &&
                    this.compare(next, false) == 0
                ) {
                    true
                } else {
                    var nextAdjusted = next
                    var thisAdjusted = this
                    if (next.compare(prev, false) < 0) {
                        nextAdjusted = MinDateTime(next).apply {
                            h = (h.toInt() + 24).toString()
                        }
                        if (this.compare(prev, false) < 0) {
                            thisAdjusted = MinDateTime(this).apply {
                                h = (h.toInt() + 24).toString()
                            }
                        }
                    }
                    if (prevInclusive) {
                        prev.compare(thisAdjusted, false) <= 0 &&
                                thisAdjusted.compare(nextAdjusted, false) < 0 ||
                                prev.compare(thisAdjusted, false) >= 0 &&
                                thisAdjusted.compare(nextAdjusted, false) > 0
                    } else {
                        prev.compare(thisAdjusted, false) < 0 &&
                                thisAdjusted.compare(nextAdjusted, false) <= 0 ||
                                prev.compare(thisAdjusted, false) > 0 &&
                                thisAdjusted.compare(nextAdjusted, false) >= 0
                    }
                }
            }

        fun MinDateTime.getNextClosestTimeIndex(
            list: List<MinDateTime>,
            prevInclusive: Boolean = true
        ): Int {
            var result = -1
            for (i in list.indices) {
                if (this.isBetween(list.getWithWrappedIndex(i - 1)!!, list[i], prevInclusive)) {
                    result = i
                    break
                }
            }
            return result
        }


    }
}