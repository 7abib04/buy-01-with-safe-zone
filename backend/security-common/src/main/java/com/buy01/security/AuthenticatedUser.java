package com.buy01.security;

import java.security.Principal;

public record AuthenticatedUser(
        String userId,
        String email,
        String role
) implements Principal {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    public boolean isSeller() {
        return "SELLER".equalsIgnoreCase(role);
    }

    @Override
    public String getName() {
        return userId;
    }
}
