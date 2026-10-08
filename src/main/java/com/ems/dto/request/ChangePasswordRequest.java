package com.ems.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Form data for the change-password page. Deliberately not {@code @Data}, so
 * the passwords can never end up in a generated toString().
 */
@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    /** 72 is the BCrypt input limit; longer passwords would be silently truncated. */
    @NotBlank(message = "New password is required")
    @Size(min = 10, max = 72, message = "Password must be 10 to 72 characters")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "Password must contain at least one letter and one digit")
    private String newPassword;

    @NotBlank(message = "Please confirm the new password")
    private String confirmPassword;
}