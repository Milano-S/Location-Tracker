package com.exclr8.locationtrackerdemo.handler

import android.os.Handler
import android.os.HandlerThread

class AndroidHandlerThread : HandlerThread(TAG) {

    private var handler: Handler? = null

    init {
        start()
        handler = Handler(looper)
    }

    fun execute(r: Runnable){
        handler?.post(r)
    }

    companion object{
        val TAG = "HandlerThreadWorker"
    }
}