package com.forensiccybertech.eagleye.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface AlertsService {
    /** GET /api/v1/alerts?page=0&size=20&sort_by=@timestamp&sort_order=desc */
    @GET("api/v1/alerts")
    suspend fun alerts(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort_by", encoded = true) sortBy: String = "%40timestamp",
        @Query("sort_order") sortOrder: String = "desc",
    ): ResponseBody
}
