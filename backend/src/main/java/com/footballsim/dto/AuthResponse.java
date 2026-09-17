package com.footballsim.dto;

public class AuthResponse {

    private String token;
    private UserResponse user;
    private Boolean adminVerificationRequired;
    private String adminVerificationToken;

    public AuthResponse(String token, UserResponse user) {
        this.token = token;
        this.user = user;
    }

    private AuthResponse() {}

    public static AuthResponse adminPending(String adminVerificationToken) {
        AuthResponse r = new AuthResponse();
        r.adminVerificationRequired = true;
        r.adminVerificationToken = adminVerificationToken;
        return r;
    }

    public String getToken() { return token; }
    public UserResponse getUser() { return user; }
    public Boolean getAdminVerificationRequired() { return adminVerificationRequired; }
    public String getAdminVerificationToken() { return adminVerificationToken; }
}
