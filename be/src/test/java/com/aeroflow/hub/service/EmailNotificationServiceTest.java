package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsTextEmailUsingConfiguredSenderAddress() {
        var service = new EmailNotificationService(mailSender, "operations@example.test");

        service.sendTextEmail("  crew@example.test ", "Crew clearance", "Your request is ready.");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("operations@example.test", message.getFrom());
        assertEquals("crew@example.test", message.getTo()[0]);
        assertEquals("Crew clearance", message.getSubject());
        assertEquals("Your request is ready.", message.getText());
    }

    @Test
    void omitsSenderAddressWhenNoMailUsernameIsConfigured() {
        var service = new EmailNotificationService(mailSender, "");

        service.sendTextEmail("crew@example.test", "Crew clearance", "Your request is ready.");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertNull(captor.getValue().getFrom());
    }

    @Test
    void rejectsBlankEmailFieldsWithoutCallingMailSender() {
        var service = new EmailNotificationService(mailSender, "operations@example.test");

        assertThrows(IllegalArgumentException.class, () -> service.sendTextEmail("", "Subject", "Body"));
        assertThrows(IllegalArgumentException.class, () -> service.sendTextEmail("to@example.test", " ", "Body"));
        assertThrows(IllegalArgumentException.class, () -> service.sendTextEmail("to@example.test", "Subject", " "));

        verifyNoInteractions(mailSender);
    }
}
