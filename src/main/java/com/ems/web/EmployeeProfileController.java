package com.ems.web;

import com.ems.dto.request.ProfileUpdateRequest;
import com.ems.dto.response.EmployeeResponse;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.EmployeeService;
import com.ems.service.interfaces.PhotoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Self-service profile for the signed-in employee. The employee is always taken
 * from the authenticated account, and only contact details can be changed.
 */
@Controller
@RequiredArgsConstructor
public class EmployeeProfileController {

    private final UserRepository userRepository;
    private final EmployeeService employeeService;
    private final PhotoService photoService;

    @GetMapping("/employee/profile")
    public String profile(
            Authentication authentication,
            Model model) {

        model.addAttribute(
                "employee",
                employeeService.getEmployeeById(currentEmployeeId(authentication)));

        return "employee-profile";
    }

    @GetMapping("/employee/profile/edit")
    public String editProfile(
            Authentication authentication,
            Model model) {

        EmployeeResponse employee =
                employeeService.getEmployeeById(currentEmployeeId(authentication));

        ProfileUpdateRequest form = new ProfileUpdateRequest();

        form.setFirstName(employee.getFirstName());
        form.setLastName(employee.getLastName());
        form.setEmail(employee.getEmail());
        form.setPhoneNumber(employee.getPhoneNumber());

        model.addAttribute("profileForm", form);

        return "employee-profile-form";
    }

    @PostMapping("/employee/profile/update")
    public String updateProfile(
            Authentication authentication,
            @Valid @ModelAttribute("profileForm") ProfileUpdateRequest form,
            BindingResult result,
            RedirectAttributes redirectAttributes) {

        if (!result.hasErrors()) {

            try {

                employeeService.updateOwnProfile(
                        currentEmployeeId(authentication),
                        form);

                redirectAttributes.addFlashAttribute(
                        "success",
                        "Your profile has been updated.");

                return "redirect:/employee/profile";

            } catch (IllegalArgumentException ex) {

                // "Email already exists." / "Phone number already exists."
                String field = ex.getMessage().startsWith("Email")
                        ? "email"
                        : "phoneNumber";

                result.rejectValue(field, "duplicate", ex.getMessage());
            }
        }

        return "employee-profile-form";
    }

    @PostMapping("/employee/profile/photo")
    public String uploadPhoto(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes) {

        try {

            photoService.storePhoto(currentEmployeeId(authentication), file);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Your photo has been updated.");

        } catch (IllegalArgumentException ex) {

            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/employee/profile";
    }

    /**
     * Resolves the employee profile linked to the signed-in account.
     */
    private Long currentEmployeeId(Authentication authentication) {

        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        if (user.getEmployee() == null) {
            throw new ResourceNotFoundException(
                    "No employee profile is linked to this account.");
        }

        return user.getEmployee().getId();
    }

}
