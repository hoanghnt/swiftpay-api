package com.hoanghnt.swiftpay.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.from:}")
    private String fromEmail;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Async
    public void sendWithdrawEmail(String toEmail, String username,
            BigDecimal amount, BigDecimal fee, BigDecimal netAmount, UUID transactionId) {
        if (!notificationsEnabled) {
            return;
        }
        try {
            Context context = new Context();
            context.setVariable("username", username);
            context.setVariable("amount", amount);
            context.setVariable("fee", fee);
            context.setVariable("netAmount", netAmount);
            context.setVariable("transactionId", transactionId);

            String htmlContent = templateEngine.process("transaction-withdraw", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("SwiftPay: Xác nhận rút tiền thành công");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Withdraw email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send withdraw email to: {}", toEmail, e);
        }
    }
}
