package com.ems.web;

import com.ems.service.interfaces.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/system-logs")
public class SystemLogController {

    private final SystemLogService systemLogService;

    @GetMapping
    public String systemLogs(

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size,

            Model model) {

        model.addAttribute(
                "logs",
                systemLogService.getLogs(page, size));

        return "system-logs";
    }

}