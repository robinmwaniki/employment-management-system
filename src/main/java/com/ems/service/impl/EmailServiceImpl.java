package com.ems.service.impl;

import com.ems.service.interfaces.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;


/**
 * Sends plain-text notification emails. A mail problem never breaks the business
 * operation that triggered it: when mail is not configured the message is skipped,
 * and delivery failures are logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
    
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Override
    public void sendEmail(
            String to,
            String subject,
            String body
    ) {

        if (mailUsername == null || mailUsername.isBlank()) {
            log.warn("Mail is not configured; skipping email '{}'", subject);
            return;
        }

        if (to == null || to.isBlank()) {
            log.warn("No recipient address; skipping email '{}'", subject);
            return;
        }

        try {

            SimpleMailMessage message = new SimpleMailMessage();

            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

        } catch (MailException ex) {
            log.error("Could not send email '{}': {}", subject, ex.getMessage());
        }
    }
}
