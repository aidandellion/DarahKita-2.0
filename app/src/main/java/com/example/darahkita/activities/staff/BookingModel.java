package com.example.darahkita.activities.staff;
import com.google.firebase.Timestamp;

public class BookingModel {
    private String id;
    private String userID, centreID, status, timeSlot;
    private Timestamp bookingDate;
    private boolean checkedIn;

    public BookingModel() {} // Required for Firestore

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserID() { return userID; }
    public String getStatus() { return status; }
    public boolean isCheckedIn() { return checkedIn; }
    public Timestamp getBookingDate() { return bookingDate; }
    // ... add others as needed
}
