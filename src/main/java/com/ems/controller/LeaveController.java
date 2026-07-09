package com.ems.controller;

import com.ems.dto.request.LeaveRequest;
import com.ems.dto.response.LeaveResponse;
import com.ems.service.interfaces.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveResponse applyLeave(
            @Valid @RequestBody LeaveRequest request) {

        return leaveService.applyLeave(request);
    }

    @GetMapping
    public Page<LeaveResponse> getAllLeaves(

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size) {

        return leaveService.getAllLeaves(page, size);
    }

    @GetMapping("/{id}")
    public LeaveResponse getLeaveById(
            @PathVariable Long id) {

        return leaveService.getLeaveById(id);
    }

    @PutMapping("/{id}/approve")
    public LeaveResponse approveLeave(
            @PathVariable Long id) {

        return leaveService.approveLeave(id);
    }

    @PutMapping("/{id}/reject")
    public LeaveResponse rejectLeave(
            @PathVariable Long id) {

        return leaveService.rejectLeave(id);
    }

    @PutMapping("/{id}/cancel")
    public LeaveResponse cancelLeave(
            @PathVariable Long id) {

        return leaveService.cancelLeave(id);
    }
}