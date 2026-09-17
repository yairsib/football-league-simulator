package com.footballsim.service;

public interface EmailService {

    void sendAdminOtp(String to, String code);

    void sendPasswordReset(String to, String code);
}
