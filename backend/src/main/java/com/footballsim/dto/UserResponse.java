package com.footballsim.dto;

import com.footballsim.entity.User;
import com.footballsim.enums.Role;

import java.math.BigDecimal;

public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private BigDecimal balance;
    private Role role;

    public UserResponse(Long id, String username, String email, BigDecimal balance, Role role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.balance = balance;
        this.role = role;
    }

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getBalance(), user.getRole());
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public BigDecimal getBalance() { return balance; }
    public Role getRole() { return role; }
}
