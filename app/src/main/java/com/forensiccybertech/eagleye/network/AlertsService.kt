package com.forensiccybertech.eagleye.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface AlertsService {
    /** GET /api/v1/alerts?page=0&size=20&sort_by=@timestamp&sort_order=desc[&severity&process_name&agent_id&event_category&from_ts&to_ts] */
    @GET("api/v1/alerts")
    suspend fun alerts(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort_by", encoded = true) sortBy: String = "%40timestamp",
        @Query("sort_order") sortOrder: String = "desc",
        @Query("severity") severity: String? = null,
        @Query("process_name") processName: String? = null,
        @Query("agent_id") agentId: String? = null,
        @Query("event_category") category: String? = null,
        @Query("from_ts") fromTs: String? = null,
        @Query("to_ts") toTs: String? = null,
    ): ResponseBody
}

interface StatsService {
    @GET("api/v1/stats/summary") suspend fun summary(): ResponseBody
    @GET("api/v1/stats/top") suspend fun top(): ResponseBody
    @GET("api/v1/stats/timeline")
    suspend fun timeline(@Query("period") period: String = "7d", @Query("interval") interval: String = "1h"): ResponseBody
}

interface HostsService {
    @GET("api/v1/hosts") suspend fun hosts(): ResponseBody
}
