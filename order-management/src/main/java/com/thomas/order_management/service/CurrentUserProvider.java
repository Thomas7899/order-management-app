package com.thomas.order_management.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Liefert den Benutzernamen für Belege und Änderungsbelege ("Angelegt von").
 */
@Component
public class CurrentUserProvider {

    public static final String SYSTEM_USER = "SYSTEM";

    public String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM_USER;
        }
        return authentication.getName();
    }
}
