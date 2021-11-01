package com.example.rxplayground.task

interface Task : Runnable {
    fun cancel()
}
