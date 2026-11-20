package com.example.darahkita.activities.donor;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.darahkita.R;
import com.example.darahkita.models.Question;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EligibilityScreeningActivity extends AppCompatActivity {
    private TextView tvQuestion, tvProgress;
    private Button btnYes, btnNo;
    private List<Question> questions;
    private int currentIndex = 0;
    private Map<String, Boolean> answers = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eligibility_screening);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvProgress = findViewById(R.id.tvProgress);
        btnYes = findViewById(R.id.btnYes);
        btnNo = findViewById(R.id.btnNo);
        questions = buildQuestions();
        showQuestion();
        btnYes.setOnClickListener(v -> handleAnswer(true));
        btnNo.setOnClickListener(v -> handleAnswer(false));
    }

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

    private void handleAnswer(boolean userAnswer) {
        Question q = questions.get(currentIndex);
        answers.put(q.getId(), userAnswer);
        boolean passed = (userAnswer == q.getEligibleAnswer());
        if (!passed) { showIneligibleDialog(q.getText()); return; }
        currentIndex++;
        if (currentIndex < questions.size()) showQuestion(); else proceedToLocationSelection();
    }

    private void showIneligibleDialog(String failedQuestionText) {
        new AlertDialog.Builder(this)
                .setTitle("Not Eligible Right Now")
                .setMessage("Based on your answer to:\n\"" + failedQuestionText + "\"\n\nyou're not eligible to donate at this time. Please check back later or consult a doctor if you have concerns.")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .show();
    }

    private void proceedToLocationSelection() {
        Intent intent = new Intent(this, SelectLocationActivity.class);
        intent.putExtra("eligibilityAnswers", (Serializable) answers);
        intent.putExtra("eligible", true);
        startActivity(intent);
        finish();
    }
}