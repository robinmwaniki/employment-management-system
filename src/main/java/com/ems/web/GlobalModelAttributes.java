package com.ems.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds values used by the shared layout (sidebar and top bar) to every page model.
 */
@ControllerAdvice(basePackages = "com.ems.web")
public class GlobalModelAttributes {

    @ModelAttribute("currentUsername")
    public String currentUsername(Authentication authentication) {

        return authentication == null ? null : authentication.getName();
    }

    /** True for administrators; used to hide admin-only menu entries. */
    @ModelAttribute("isAdmin")
    public boolean isAdmin(Authentication authentication) {

        return authentication != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}