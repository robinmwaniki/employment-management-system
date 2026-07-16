package com.ems.service.interfaces;

public interface EmailService {

    void sendEmail(
            String to,
            String subject,
            String body);

}