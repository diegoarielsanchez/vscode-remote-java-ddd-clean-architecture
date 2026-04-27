package com.das.msr.application;

public class ErrorMainResponse {
    private int statusCode;
    private String message;

    public ErrorMainResponse(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }
}
