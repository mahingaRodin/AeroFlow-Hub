package com.aeroflow.hub.config;

import jakarta.annotation.PostConstruct;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class MailConfig {

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String host;

    @Value("${spring.mail.port:587}")
    private int port;

    @Value("${spring.mail.protocol:smtp}")
    private String protocol;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Value("${MAIL_SMTP_AUTH:true}")
    private boolean smtpAuth;

    @Value("${MAIL_SMTP_STARTTLS:true}")
    private boolean smtpStartTls;

    @Bean
    JavaMailSender javaMailSender() {
        String normalizedUsername = normalizeUsername(username);
        String normalizedPassword = normalizePassword(password);

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setProtocol(protocol);
        sender.setUsername(normalizedUsername);
        sender.setPassword(normalizedPassword);
        sender.setDefaultEncoding("UTF-8");

        Properties props = new Properties();
        props.put("mail.smtp.auth", String.valueOf(smtpAuth));
        props.put("mail.smtp.starttls.enable", String.valueOf(smtpStartTls));
        props.put("mail.smtp.starttls.required", String.valueOf(smtpStartTls));
        props.put("mail.smtp.ssl.trust", host);
        props.put("mail.smtp.auth.mechanisms", "PLAIN LOGIN");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        if (StringUtils.hasText(normalizedUsername)) {
            props.put("mail.smtp.from", normalizedUsername);
        }
        sender.setJavaMailProperties(props);
        return sender;
    }

    @PostConstruct
    void logMailConfiguration() {
        String normalizedUsername = normalizeUsername(username);
        boolean passwordConfigured = StringUtils.hasText(normalizePassword(password));
        if (!StringUtils.hasText(normalizedUsername) || !passwordConfigured) {
            log.warn(
                    "SMTP is not fully configured (username or password missing). "
                            + "OTP email delivery will fail until MAIL_USERNAME and MAIL_PASSWORD are set.");
            return;
        }
        log.info("SMTP configured for host {}:{} as {}", host, port, normalizedUsername);
    }

    static String normalizeUsername(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    /** Gmail app passwords are often copied with spaces — strip them. */
    static String normalizePassword(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replace(" ", "");
    }
}

