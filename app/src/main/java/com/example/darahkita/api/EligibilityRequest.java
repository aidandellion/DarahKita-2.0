package com.example.darahkita.api;

import java.util.Map;

/** Body of POST eligibility/check: {"answers": {"goodHealth": true, ...}} */
public class EligibilityRequest {
    public Map<String, Boolean> answers;

    public EligibilityRequest(Map<String, Boolean> answers) {
        this.answers = answers;
    }
}
