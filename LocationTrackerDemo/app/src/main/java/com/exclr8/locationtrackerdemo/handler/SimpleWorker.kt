package com.exclr8.locationtrackerdemo.handler

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class SimpleWorker : Thread(TAG){

    companion object{
        val TAG = "SimpleWorker"
    }

    private var alive: AtomicBoolean = AtomicBoolean(true)
    private val tasksQueue =  ConcurrentLinkedQueue<Runnable>()

    init {
        start()
    }

    override fun run() {
        super.run()
        while(alive.get()){
            tasksQueue.poll()?.run()
        }
    }

    fun execute(r: Runnable){
        tasksQueue.add(r)
    }

    fun quit(){
        alive.set(false)
    }
}