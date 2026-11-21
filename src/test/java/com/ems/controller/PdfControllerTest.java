package com.ems.controller;

import com.ems.service.interfaces.PdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the payslip download HTTP contract independently of PDF rendering.
 */
@ExtendWith(MockitoExtension.class)
class PdfControllerTest {

    @Mock
    private PdfService pdfService;

    @InjectMocks
    private PdfController pdfController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(pdfController).build();
    }

    @Test
    void downloadPayslipReturnsPdfBytesAndAttachmentHeaders() throws Exception {
        Long payrollId = 42L;
        byte[] pdfBytes = new byte[] {
                '%', 'P', 'D', 'F', '-', '1', '.', '7', '\n', '%', 'T', 'E', 'S', 'T'
        };
        when(pdfService.generatePayslip(payrollId)).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/payslips/{payrollId}", payrollId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment")))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("Payslip-42.pdf")))
                .andExpect(content().bytes(pdfBytes));

        verify(pdfService).generatePayslip(payrollId);
    }
}
