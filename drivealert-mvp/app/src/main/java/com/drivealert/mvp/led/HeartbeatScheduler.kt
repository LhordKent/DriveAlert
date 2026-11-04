package com.drivealert.mvp.led

import android.os.Handler
import android.os.Looper

interface HeartbeatScheduler {
    fun start(intervalMs: Long, task: () -> Unit)
    fun stop()
}

class MainThreadHeartbeatScheduler : HeartbeatScheduler {
    private val handler = Handler(Looper.getMainLooper())
    private var intervalMs = 0L
    private var task: (() -> Unit)? = null
    private val runnable = object : Runnable {
        override fun run() {
            task?.invoke()
            if (task != null) handler.postDelayed(this, intervalMs)
        }
    }

    override fun start(intervalMs: Long, task: () -> Unit) {
        stop()
        this.intervalMs = intervalMs
        this.task = task
        handler.postDelayed(runnable, intervalMs)
    }

    override fun stop() {
        handler.removeCallbacks(runnable)
        task = null
    }
}
