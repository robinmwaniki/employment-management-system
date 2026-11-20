package com.ems.web;

import com.ems.service.interfaces.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the server-rendered system log administration page.
 *
 * <p>The explicit bean name avoids a collision with the REST controller in
 * {@code com.ems.controller}, which has the same simple class name.</p>
 */
@Controller("systemLogPageController")
@RequiredArgsConstructor
@RequestMapping("/system-logs")
public class SystemLogController {

    private final SystemLogService systemLogService;

    /**
     * Loads a page of system log records for the Thymeleaf view.
     *
     * @param page zero-based page index
     * @param size maximum number of records to display
     * @param model model used to render the page
     * @return the system log view name
     */
    @GetMapping
    public String systemLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        model.addAttribute("logs", systemLogService.getLogs(page, size));
        return "system-logs";
    }
}
