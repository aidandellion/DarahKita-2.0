package com.example.darahkita.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/** DarahKita REST API endpoints (relative to ApiClient.BASE_URL). See api/docs/API.md. */
public interface DarahKitaApi {

    /** Questionnaire + 56-day rule. 200 with eligible/reasons/nextEligibleDate. */
    @POST("eligibility/check")
    Call<EligibilityResponse> checkEligibility(@Body EligibilityRequest request);
}
