package com.reeman.commons.utils

import android.os.CountDownTimer

abstract class PausableCountDownTimer(
    private val millisInFuture: Long,
    private val countDownInterval: Long,
) {
    private var remainingTime: Long = millisInFuture
    private var timer: CountDownTimer? = null
    private var isPaused = true

    fun isPaused() = isPaused
    abstract fun onTick(millisUntilFinished:Long)

    abstract fun onFinish()

    fun start() {
        if (isPaused) {
            isPaused = false
            timer = object : CountDownTimer(remainingTime, countDownInterval) {
                override fun onTick(millisUntilFinished: Long) {
                    remainingTime = millisUntilFinished
                    this@PausableCountDownTimer.onTick(millisUntilFinished)
                }

                override fun onFinish() {
                    isPaused = true
                    remainingTime = 0
                    this@PausableCountDownTimer.onFinish()
                }
            }.start()
        }
    }

    fun pause() {
        if (!isPaused) {
            isPaused = true
            timer?.cancel()
        }
    }

    fun cancel() {
        timer?.cancel()
        remainingTime = millisInFuture
        isPaused = false
    }

    fun resume() = start()
}
