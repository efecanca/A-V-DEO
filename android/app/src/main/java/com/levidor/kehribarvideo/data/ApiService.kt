package com.levidor.kehribarvideo.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path

interface ApiService {

    @GET("health")
    suspend fun health(): retrofit2.Response<okhttp3.ResponseBody>

    @GET("capabilities")
    suspend fun getCapabilities(): CapabilitiesResponse

    @Multipart
    @POST("generate")
    suspend fun generateVideo(
        @Part images: List<MultipartBody.Part>,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>
    ): GenerateResponse

    @GET("status/{job_id}")
    suspend fun getStatus(@Path("job_id") jobId: String): StatusResponse
}
