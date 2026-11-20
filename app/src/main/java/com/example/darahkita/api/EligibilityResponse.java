package com.example.darahkita.api;

import java.util.List;

/** Response of POST eligibility/check. nextEligibleDate is "YYYY-MM-DD" or null. */
public class EligibilityResponse {
    public boolean eligible;
    public List<String> reasons;
    public String nextEligibleDate;
}
