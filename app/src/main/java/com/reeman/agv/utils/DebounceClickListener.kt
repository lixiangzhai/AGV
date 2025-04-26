package com.reeman.agv.utils

import android.content.res.Resources
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.*
import timber.log.Timber

interface DebounceClickListener {
    val clickDelay: Long
        get() = 300L

    fun View.setDebounceClickListener(listener: (View) -> Unit) {
        this.setOnClickListener { view ->
            ClickJobManager.clickJobs.values.forEach { it?.cancel() }
            ClickJobManager.clickJobs.clear()
            val newJob = CoroutineScope(Dispatchers.Main).launch {
                delay(clickDelay)
                val viewIdName = try {
                    resources.getResourceEntryName(view.id)
                } catch (e: Resources.NotFoundException) {
                    "unknown"
                }
                if (view is TextView) {
                    Timber.tag(this@DebounceClickListener::class.java.simpleName).w("点击[${view.text}] (ID: $viewIdName)")
                } else {
                    Timber.tag(this@DebounceClickListener::class.java.simpleName).w("点击View (ID: $viewIdName)")
                }
                listener(view)
                ClickJobManager.clickJobs.remove(view)
            }
            ClickJobManager.clickJobs[view] = newJob
        }
    }
}


object ClickJobManager {
    val clickJobs: MutableMap<View, Job?> = mutableMapOf()
}