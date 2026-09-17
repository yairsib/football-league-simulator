package com.footballsim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class RealEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(RealEmailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public RealEmailService(JavaMailSender mailSender,
                            @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendAdminOtp(String to, String code) {
        send(to, "Your admin verification code",
                "Your admin verification code is: " + code + "\n\nThis code expires in 5 minutes. Do not share it.");
    }

    @Override
    public void sendPasswordReset(String to, String code) {
        send(to, "Your password reset code",
                "Your password reset code is: " + code + "\n\nThis code expires in 10 minutes. Do not share it.");
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new IllegalStateException("Email delivery failed. Please try again later.");
        }
    }
}
