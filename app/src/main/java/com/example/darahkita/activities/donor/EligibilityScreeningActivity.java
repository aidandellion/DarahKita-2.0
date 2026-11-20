package com.example.darahkita.activities.donor;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.darahkita.R;
import com.example.darahkita.api.ApiClient;
import com.example.darahkita.api.EligibilityRequest;
import com.example.darahkita.api.EligibilityResponse;
import com.example.darahkita.models.Question;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Asks the 9 screening questions, then lets the DarahKita API decide eligibility
 * (POST eligibility/check: questionnaire + 56-day rule). No decision is made on the phone.
 */
public class EligibilityScreeningActivity extends AppCompatActivity {
    private static final String TAG = "EligibilityScreening";

    private TextView tvQuestion, tvProgress;
    private Button btnYes, btnNo;
    private ProgressBar progressBar;
    private List<Question> questions;
    private int currentIndex = 0;
    private Map<String, Boolean> answers = new HashMap<>();
    private Call<EligibilityResponse> pendingCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eligibility_screening);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvProgress = findViewById(R.id.tvProgress);
        btnYes = findViewById(R.id.btnYes);
        btnNo = findViewById(R.id.btnNo);
        progressBar = findViewById(R.id.progressBar);
        questions = buildQuestions();
        showQuestion();
        btnYes.setOnClickListener(v -> handleAnswer(true));
        btnNo.setOnClickListener(v -> handleAnswer(false));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingCall != null) pendingCall.cancel();
    }

    // Ids must match the API (api/docs/API.md). The eligibleAnswer values are kept for reference;
    // the API holds the same rules and makes the decision.
    private List<Question> buildQuestions() {
        List<Question> list = new ArrayList<>();
        list.add(new Question("goodHealth", "Are you currently in good health?", true));
        list.add(new Question("ageValid", "Are you aged between 18 and 60 years old?", true));
        list.add(new Question("weightValid", "Do you weigh at least 45 kg?", true));
        list.add(new Question("recentDonation", "Have you donated blood in the last 56 days?", false));
        list.add(new Question("onMedication", "Are you currently taking any prescribed medication or antibiotics?", false));
        list.add(new Question("recentVaccination", "Have you received any vaccination in the past 4 weeks?", false));
        list.add(new Question("recentTattooOrSurgery", "Have you had a tattoo, piercing, or any surgery/dental procedure in the last 6 months?", false));
        list.add(new Question("pregnantOrBreastfeeding", "Are you currently pregnant, breastfeeding, or given birth in the last 6 months?", false));
        list.add(new Question("chronicIllness", "Do you have any chronic illness (e.g. diabetes, heart disease, hepatitis, HIV)?", false));
        return list;
    }

    private void showQuestion() {
        Question q = questions.get(currentIndex);
        tvQuestion.setText(q.getText());
        tvProgress.setText("Question " + (currentIndex + 1) + " of " + questions.size());
    }

    /** Only records the answer; after the last question all 9 answers go to the API once. */
    private void handleAnswer(boolean userAnswer) {
        if (currentIndex >= questions.size()) return; // ignore taps while checking
        Question q = questions.get(currentIndex);
        answers.put(q.getId(), userAnswer);
        currentIndex++;
        if (currentIndex < questions.size()) showQuestion(); else checkEligibilityWithApi();
    }

    private void checkEligibilityWithApi() {
        setLoading(true);
        pendingCall = ApiClient.get().checkEligibility(new EligibilityRequest(new HashMap<>(answers)));
        pendingCall.enqueue(new Callback<EligibilityResponse>() {
            @Override
            public void onResponse(@NonNull Call<EligibilityResponse> call, @NonNull Response<EligibilityResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                EligibilityResponse body = response.body();
                if (!response.isSuccessful() || body == null) {
                    Log.w(TAG, "eligibility/check failed: HTTP " + response.code());
                    showServerErrorDialog();
                    return;
                }
                if (body.eligible) {
                    proceedToLocationSelection();
                } else {
                    showIneligibleDialog(body.reasons);
                }
            }

            @Override
            public void onFailure(@NonNull Call<EligibilityResponse> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) return;
                Log.w(TAG, "eligibility/check unreachable", t);
                setLoading(false);
                showServerErrorDialog();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnYes.setEnabled(!loading);
        btnNo.setEnabled(!loading);
        if (loading) tvProgress.setText("Checking your answers...");
    }

    private void showIneligibleDialog(List<String> reasons) {
        StringBuilder msg = new StringBuilder("Based on your answers, you're not eligible to donate at this time:\n");
        if (reasons != null) {
            for (String reason : reasons) msg.append("\n• ").append(reason);
        }
        msg.append("\n\nPlease check back later or consult a doctor if you have concerns.");

        new AlertDialog.Builder(this)
                .setTitle("Not Eligible Right Now")
                .setMessage(msg.toString())
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .show();
    }

    private void showServerErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cannot reach the server")
                .setMessage("We couldn't check your answers right now. Please check your internet connection and try again. "
                        + "The first check can take up to a minute while the server wakes up.")
                .setCancelable(false)
                .setPositiveButton("Try again", (dialog, which) -> checkEligibilityWithApi())
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .show();
    }

    // Same extras as before: SelectLocationActivity / SelectDateTimeActivity read "eligibilityAnswers".
    private void proceedToLocationSelection() {
        Intent intent = new Intent(this, SelectLocationActivity.class);
        intent.putExtra("eligibilityAnswers", (Serializable) answers);
        intent.putExtra("eligible", true);
        startActivity(intent);
        finish();
    }
}
