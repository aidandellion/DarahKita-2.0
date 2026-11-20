package com.example.darahkita.models;

import com.google.firebase.firestore.GeoPoint;
import java.util.Date;

public class Booking {
    private String userID;
    private String centreID;
    private Date bookingDate;
    private String timeslot;
    private String status;
    private boolean checkedIn;
    private GeoPoint checkinLocation;

    public Booking() {}

    public Booking(String userID, String centreID, Date bookingDate, String timeslot) {
        this.userID = userID;
        this.centreID = centreID;
        this.bookingDate = bookingDate;
        this.timeslot = timeslot;
        this.status = "Upcoming";
        this.checkedIn = false;
        this.checkinLocation = null;
    }

    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }
    public String getCentreID() { return centreID; }
    public void setCentreID(String centreID) { this.centreID = centreID; }
    public Date getBookingDate() { return bookingDate; }
    public void setBookingDate(Date bookingDate) { this.bookingDate = bookingDate; }
    public String getTimeslot() { return timeslot; }
    public void setTimeslot(String timeslot) { this.timeslot = timeslot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isCheckedIn() { return checkedIn; }
    public void setCheckedIn(boolean checkedIn) { this.checkedIn = checkedIn; }
    public GeoPoint getCheckinLocation() { return checkinLocation; }
    public void setCheckinLocation(GeoPoint checkinLocation) { this.checkinLocation = checkinLocation; }
}