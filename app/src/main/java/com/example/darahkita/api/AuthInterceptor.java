package com.example.darahkita.api;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Adds "Authorization: Bearer <Firebase ID token>" to every API request.
 * Runs on OkHttp's background thread, so blocking on Tasks.await() is safe here
 * (never call the API synchronously from the main thread).
 */
public class AuthInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // No logged-in user: send without a token, the API answers 401.
            return chain.proceed(request);
        }

        String token;
        try {
            // false = reuse the cached token, Firebase refreshes it itself when it is about to expire
            GetTokenResult result = Tasks.await(user.getIdToken(false), 30, TimeUnit.SECONDS);
            token = result.getToken();
        } catch (Exception e) {
            // Only IOException reaches Retrofit's onFailure; anything else would crash OkHttp's thread.
            throw new IOException("Could not get Firebase ID token", e);
        }
        if (token == null) throw new IOException("Firebase ID token is empty");

        return chain.proceed(request.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build());
    }
}
