package com.levidor.kehribarvideo.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {

    @Multipart
    @POST("generate")
    suspend fun generateVideo(
        @Part image: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody,
        @Part("negative_prompt") negativePrompt: RequestBody
    ): GenerateResponse

    @GET("status/{job_id}")
    suspend fun getStatus(@Path("job_id") jobId: String): StatusResponse
}
