package com.footballsim.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminVerifyRequest {

    @NotBlank(message = "Verification token is required")
    private String adminVerificationToken;

    @NotBlank(message = "Code is required")
    private String code;

    public String getAdminVerificationToken() { return adminVerificationToken; }
    public void setAdminVerificationToken(String adminVerificationToken) { this.adminVerificationToken = adminVerificationToken; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
