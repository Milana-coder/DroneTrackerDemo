package com.example.dronetrackerdemo.data

import retrofit2.http.GET

interface AirApi {

    @GET("objects")
    suspend fun getObjects(): List<AirObject>

}
