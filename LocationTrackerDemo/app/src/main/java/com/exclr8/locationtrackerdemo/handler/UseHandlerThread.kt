package com.exclr8.locationtrackerdemo.handler

import android.os.Handler
import android.os.HandlerThread

class UseHandlerThread {

    val TAG = "UseHandlerThread"
    val thread = HandlerThread(TAG)
    var handler: Handler? = null

    init {
        start()
    }

    fun start(){
        //This will prepare looper
        thread.start()

        //You can get looper only after handler is started
        handler = Handler(thread.looper)
    }

    fun execute(r: Runnable){
        //We can post tasks only with Handler associated with the HandlerThread
        handler?.post(r)
    }

    fun quit(){
        thread.quit()
    }
}