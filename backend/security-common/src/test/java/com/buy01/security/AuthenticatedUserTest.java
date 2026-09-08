package com.buy01.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthenticatedUserTest {

    @Test
    void isAdminIsCaseInsensitive() {
        assertThat(new AuthenticatedUser("u1", "a@example.com", "admin").isAdmin()).isTrue();
        assertThat(new AuthenticatedUser("u1", "a@example.com", "CLIENT").isAdmin()).isFalse();
    }

    @Test
    void isSellerIsCaseInsensitive() {
        assertThat(new AuthenticatedUser("u1", "a@example.com", "seller").isSeller()).isTrue();
        assertThat(new AuthenticatedUser("u1", "a@example.com", "CLIENT").isSeller()).isFalse();
    }

    @Test
    void getNameReturnsUserId() {
        AuthenticatedUser user = new AuthenticatedUser("u1", "a@example.com", "CLIENT");
        assertThat(user.getName()).isEqualTo("u1");
    }
}
