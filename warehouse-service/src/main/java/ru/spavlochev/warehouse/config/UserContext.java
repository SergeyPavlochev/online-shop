package ru.spavlochev.warehouse.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class UserContext {

    private UserContext() {}

    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User not authenticated");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getSubject();
    }

    public static List<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User not authenticated");
        }
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();
    }
}
