package com.example.darahkita.models;

public class Centre {
    private String centreId, name, address, phone, operatingHours;
    private double latitude, longitude;

    public Centre() {} // required no-arg constructor for Firestore

    public Centre(String centreId, String name, String address, double latitude, double longitude) {
        this.centreId = centreId; this.name = name; this.address = address;
        this.latitude = latitude; this.longitude = longitude;
    }

    public String getCentreId() { return centreId; }
    public void setCentreId(String centreId) { this.centreId = centreId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getOperatingHours() { return operatingHours; }
    public void setOperatingHours(String operatingHours) { this.operatingHours = operatingHours; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
}