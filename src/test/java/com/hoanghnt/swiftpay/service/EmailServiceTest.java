package com.hoanghnt.swiftpay.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceTest {

    @Mock
    private org.springframework.mail.javamail.JavaMailSender mailSender;
    @Mock
    private TemplateEngine templateEngine;
    @InjectMocks
    private EmailService emailService;

    private void configure(String host, String from, boolean notificationsEnabled) {
        ReflectionTestUtils.setField(emailService, "mailHost", host);
        ReflectionTestUtils.setField(emailService, "fromEmail", from);
        ReflectionTestUtils.setField(emailService, "notificationsEnabled", notificationsEnabled);
        ReflectionTestUtils.invokeMethod(emailService, "resolveDeliveryMode");
    }

    private void sendOne() {
        emailService.sendTopupSuccessEmail("u@example.com", "u", new BigDecimal("1000.0000"), UUID.randomUUID());
    }

    @Test
    @DisplayName("MAIL_HOST rỗng → KHÔNG chạm tới mailSender, kể cả khi notifications bật")
    void blankHostDisablesDelivery() {
        configure("", "no-reply@swiftpay.local", true);

        sendOne();

        assertThat((Boolean) ReflectionTestUtils.getField(emailService, "deliveryEnabled")).isFalse();
        verifyNoInteractions(mailSender);
        verifyNoInteractions(templateEngine);
    }

    @Test
    @DisplayName("MAIL_FROM rỗng cũng đủ để tắt gửi — cấu hình nửa vời không được coi là hợp lệ")
    void blankFromDisablesDelivery() {
        configure("smtp.example.com", "  ", true);

        sendOne();

        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("notifications.enabled=false → tắt dù mail đã cấu hình đầy đủ")
    void flagOverridesConfiguredMail() {
        configure("smtp.example.com", "no-reply@swiftpay.local", false);

        sendOne();

        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("Cấu hình đầy đủ → thật sự gửi")
    void fullyConfiguredSends() {
        configure("smtp.example.com", "no-reply@swiftpay.local", true);
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(templateEngine.process(any(String.class), any())).thenReturn("<html/>");

        sendOne();

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("SMTP hỏng (RuntimeException) KHÔNG được thoát ra ngoài — quy tắc #6, tiền không phụ thuộc email")
    void smtpFailureNeverEscapes() {
        configure("smtp.example.com", "no-reply@swiftpay.local", true);
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(templateEngine.process(any(String.class), any())).thenReturn("<html/>");
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(this::sendOne).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Không có template nào được render khi delivery tắt — tiết kiệm cả CPU, không chỉ I/O")
    void disabledSkipsTemplateRendering() {
        configure(null, null, true);

        emailService.sendTransferSentEmail("a@example.com", "a", "b", BigDecimal.ONE, UUID.randomUUID());
        emailService.sendTransferReceivedEmail("b@example.com", "b", "a", BigDecimal.ONE, UUID.randomUUID());
        sendOne();

        verify(templateEngine, never()).process(any(String.class), any());
    }
}
