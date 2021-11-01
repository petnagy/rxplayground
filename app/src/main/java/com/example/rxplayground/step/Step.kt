package com.example.rxplayground.step

import io.reactivex.Completable

interface Step {
    fun run(): Completable
}
