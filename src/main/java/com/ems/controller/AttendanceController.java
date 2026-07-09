package com.ems.controller;

import com.ems.dto.request.AttendanceRequest;
import com.ems.dto.response.AttendanceResponse;
import com.ems.service.interfaces.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceResponse checkIn(
            @Valid @RequestBody AttendanceRequest request) {

        return attendanceService.checkIn(request);
    }

    @PutMapping("/check-out/{employeeId}")
    public AttendanceResponse checkOut(
            @PathVariable Long employeeId) {

        return attendanceService.checkOut(employeeId);
    }

    @GetMapping
    public Page<AttendanceResponse> getAllAttendance(

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size) {

        return attendanceService.getAllAttendance(page, size);
    }

    @GetMapping("/{id}")
    public AttendanceResponse getAttendanceById(
            @PathVariable Long id) {

        return attendanceService.getAttendanceById(id);
    }

}