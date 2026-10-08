package com.ems.web;

import com.ems.dto.request.ChangePasswordRequest;
import com.ems.security.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Lets a signed-in user change their own password.
 */
@Controller
@RequiredArgsConstructor
public class EmployeeAccountController {

    private final UserService userService;

    @GetMapping("/employee/change-password")
    public String changePasswordForm(Model model) {

        model.addAttribute("passwordForm", new ChangePasswordRequest());

        return "change-password";
    }

    @PostMapping("/employee/change-password")
    public String changePassword(

            Authentication authentication,

            @Valid @ModelAttribute("passwordForm") ChangePasswordRequest form,

            BindingResult result,

            RedirectAttributes redirectAttributes) {

        if (!result.hasErrors()
                && !form.getNewPassword().equals(form.getConfirmPassword())) {

            result.rejectValue(
                    "confirmPassword",
                    "mismatch",
                    "New password and confirmation do not match");
        }

        if (!result.hasErrors()) {

            try {

                userService.changePassword(
                        authentication.getName(),
                        form.getCurrentPassword(),
                        form.getNewPassword());

                redirectAttributes.addFlashAttribute(
                        "success",
                        "Your password has been updated.");

                return "redirect:/employee/change-password";

            } catch (BadCredentialsException ex) {

                result.rejectValue(
                        "currentPassword",
                        "incorrect",
                        "Current password is incorrect");

            } catch (IllegalArgumentException ex) {

                result.rejectValue(
                        "newPassword",
                        "unchanged",
                        ex.getMessage());
            }
        }

        return "change-password";
    }
}