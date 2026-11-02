package com.aeroflow.hub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailNotificationService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendTextEmail(String recipient, String subject, String body) {
        if (!StringUtils.hasText(recipient)) {
            throw new IllegalArgumentException("Email recipient must not be blank");
        }
        if (!StringUtils.hasText(subject)) {
            throw new IllegalArgumentException("Email subject must not be blank");
        }
        if (!StringUtils.hasText(body)) {
            throw new IllegalArgumentException("Email body must not be blank");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient.strip());
        message.setSubject(subject);
        message.setText(body);
        if (StringUtils.hasText(fromAddress)) {
            message.setFrom(fromAddress.strip());
        }
        mailSender.send(message);
    }
}
