package com.example.dronetrackerdemo.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object RetrofitClient {


    private const val BASE_URL =
        "http://192.168.0.102:8000/"


    val api: AirApi by lazy {


        Retrofit.Builder()

            .baseUrl(BASE_URL)

            .addConverterFactory(
                GsonConverterFactory.create()
            )

            .build()

            .create(AirApi::class.java)


    }

}