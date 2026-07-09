package com.ems.service.interfaces;

import com.ems.dto.request.PayrollRequest;
import com.ems.dto.response.PayrollResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface PayrollService {

    PayrollResponse generatePayroll(PayrollRequest request);

    PayrollResponse getPayroll(Long id);

    List<PayrollResponse> getEmployeePayrolls(Long employeeId);

    Page<PayrollResponse> getAllPayrolls(int page,int size);

    PayrollResponse updatePayroll(Long id, PayrollRequest request);

    void deletePayroll(Long id);

}