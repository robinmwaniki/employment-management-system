package com.ems.service.interfaces;

import com.ems.dto.request.LeaveRequest;
import com.ems.dto.response.LeaveResponse;
import org.springframework.data.domain.Page;

public interface LeaveService {

    LeaveResponse applyLeave(LeaveRequest request);

    Page<LeaveResponse> getAllLeaves(int page, int size);
    Page<LeaveResponse> getEmployeeLeaves(
            Long employeeId,
            int page,
            int size);
    LeaveResponse getLeaveById(Long id);

    LeaveResponse approveLeave(Long id);

    LeaveResponse rejectLeave(Long id);

    LeaveResponse cancelLeave(Long id);

}