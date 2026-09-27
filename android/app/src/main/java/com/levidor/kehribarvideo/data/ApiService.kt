package com.levidor.kehribarvideo.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path

interface ApiService {

    @GET("health")
    suspend fun health(): retrofit2.Response<HealthResponse>

    @GET("capabilities")
    suspend fun getCapabilities(): CapabilitiesResponse

    @Multipart
    @POST("prepare-reference")
    suspend fun prepareReference(
        @Part image: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody
    ): ReferenceResponse

    @Multipart
    @POST("studio/generate")
    suspend fun generateStudioImage(
        @Part productImage: MultipartBody.Part,
        @Part modelImage: MultipartBody.Part?,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>
    ): StudioGenerateResponse

    @Multipart
    @POST("studio/revise")
    suspend fun reviseStudioImage(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>
    ): StudioGenerateResponse

    @FormUrlEncoded
    @POST("studio/results/{result_id}/approve")
    suspend fun approveStudioResult(
        @Path("result_id") resultId: String,
        @Field("approved") approved: String = "true"
    ): ApprovalResponse

    @FormUrlEncoded
    @POST("studio/results/{result_id}/video")
    suspend fun generateStudioVideo(
        @Path("result_id") resultId: String,
        @FieldMap fields: Map<String, String>
    ): StudioGenerateResponse

    @Multipart
    @POST("generate")
    suspend fun generateVideo(
        @Part images: List<MultipartBody.Part>,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>
    ): GenerateResponse

    @GET("status/{job_id}")
    suspend fun getStatus(@Path("job_id") jobId: String): StatusResponse

    @GET("projects")
    suspend fun getProjects(): ProjectsResponse

    @GET("projects/{project_id}")
    suspend fun getProject(@Path("project_id") projectId: String): ProjectResponse

    @GET("gallery")
    suspend fun getGallery(): GalleryResponse
}
