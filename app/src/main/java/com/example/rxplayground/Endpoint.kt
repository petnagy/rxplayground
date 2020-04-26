package com.example.rxplayground

import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query

interface Endpoint {

    @GET("/test")
    fun callEndpoint(@Query("chunkNumber") chunkNumber: Int) : Single<ChunkResponse>
}

data class ChunkResponse(val chunkNumber: Int)
