package com.ems.web;

import com.ems.entity.LeaveStatus;
import com.ems.repository.AttendanceRepository;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.LeaveRepository;
import com.ems.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRepository leaveRepository;
    private final PayrollRepository payrollRepository;
    private final AttendanceRepository attendanceRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        model.addAttribute("employees",
                employeeRepository.count());

        model.addAttribute("departments",
                departmentRepository.count());

        model.addAttribute("leaves",
                leaveRepository.count());

        model.addAttribute("payrolls",
                payrollRepository.count());

        model.addAttribute(
                "recentEmployees",
                employeeRepository.findTop5ByOrderByIdDesc());

        model.addAttribute(
                "presentToday",
                attendanceRepository.countByAttendanceDate(LocalDate.now()));

        model.addAttribute(
                "lateToday",
                attendanceRepository.countByAttendanceDateAndLate(
                        LocalDate.now(),
                        true));

        model.addAttribute(
                "pendingLeaves",
                leaveRepository.countByStatus(
                        LeaveStatus.PENDING));

        model.addAttribute(
                "totalPayroll",
                employeeRepository.getTotalPayroll());

        model.addAttribute(
                "averageSalary",
                employeeRepository.getAverageSalary());

        model.addAttribute(
                "highestSalary",
                employeeRepository.getHighestSalary());

        model.addAttribute(
                "lowestSalary",
                employeeRepository.getLowestSalary());

        return "dashboard";
    }

}