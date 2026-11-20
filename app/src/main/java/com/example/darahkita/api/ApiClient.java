package com.example.darahkita.api;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Single shared Retrofit instance for the DarahKita REST API. */
public final class ApiClient {

    // Must end with "/" (Retrofit requirement). Contract: api/docs/API.md
    public static final String BASE_URL = "https://darahkita-api.onrender.com/v1/";

    // The free host sleeps when idle and can take up to a minute to wake up.
    private static final int TIMEOUT_SECONDS = 60;

    private static volatile DarahKitaApi api;

    private ApiClient() {}

    public static DarahKitaApi get() {
        if (api == null) {
            synchronized (ApiClient.class) {
                if (api == null) {
                    // BASIC = method, URL, status and timing only; never logs headers (token) or bodies.
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
                    logging.redactHeader("Authorization");

                    OkHttpClient client = new OkHttpClient.Builder()
                            .addInterceptor(new AuthInterceptor())
                            .addInterceptor(logging)
                            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .build();

                    api = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                            .create(DarahKitaApi.class);
                }
            }
        }
        return api;
    }
}
