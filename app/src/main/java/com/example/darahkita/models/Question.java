package com.example.darahkita.models;

public class Question {
    private String id, text;
    private boolean eligibleAnswer;

    public Question(String id, String text, boolean eligibleAnswer) {
        this.id = id; this.text = text; this.eligibleAnswer = eligibleAnswer;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public boolean getEligibleAnswer() { return eligibleAnswer; }
}