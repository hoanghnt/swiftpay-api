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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.from}")
    private String fromEmail;

    @Value("${app.email.verification-url}")
    private String verificationBaseUrl;

    @Value("${app.email.reset-password-url}")
    private String resetPasswordBaseUrl;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Async
    public void sendVerificationEmail(String toEmail, String username, String token) {
        try {
            String verificationLink = verificationBaseUrl + "?token=" + token;

            Context context = new Context();
            context.setVariables(Map.of(
                    "username", username,
                    "verificationLink", verificationLink));

            String htmlContent = templateEngine.process("email-verification", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Verify your SwiftPay account");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Verification email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to: {}", toEmail, e);
        }
    }

    @Async
    public void sendResetPasswordEmail(String toEmail, String username, String token) {
        try {
            String resetLink = resetPasswordBaseUrl + "?token=" + token;
            Context context = new Context();
            context.setVariables(Map.of(
                    "username", username,
                    "resetLink", resetLink));
            String htmlContent = templateEngine.process("reset-password", context);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Reset your SwiftPay password");
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Reset password email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send reset password email to: {}", toEmail, e);
        }
    }

    @Async
    public void sendTransferSentEmail(String toEmail, String senderUsername,
            String receiverUsername, BigDecimal amount, UUID transactionId) {
        if (!notificationsEnabled) {
            return;
        }
        try {
            Context context = new Context();
            context.setVariables(Map.of(
                    "senderUsername", senderUsername,
                    "receiverUsername", receiverUsername,
                    "amount", amount,
                    "transactionId", transactionId));

            String htmlContent = templateEngine.process("transaction-sent", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("SwiftPay: Bạn vừa chuyển tiền thành công");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Transfer-sent email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send transfer-sent email to: {}", toEmail, e);
        }
    }

    @Async
    public void sendTransferReceivedEmail(String toEmail, String receiverUsername,
            String senderUsername, BigDecimal amount, UUID transactionId) {
        if (!notificationsEnabled) {
            return;
        }
        try {
            Context context = new Context();
            context.setVariables(Map.of(
                    "receiverUsername", receiverUsername,
                    "senderUsername", senderUsername,
                    "amount", amount,
                    "transactionId", transactionId));

            String htmlContent = templateEngine.process("transaction-received", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("SwiftPay: Bạn vừa nhận được tiền");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Transfer-received email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send transfer-received email to: {}", toEmail, e);
        }
    }

    @Async
    public void sendWithdrawEmail(String toEmail, String username,
            BigDecimal amount, BigDecimal fee, BigDecimal netAmount, UUID transactionId) {
        if (!notificationsEnabled) {
            return;
        }
        try {
            Context context = new Context();
            context.setVariables(Map.of(
                    "username", username,
                    "amount", amount,
                    "fee", fee,
                    "netAmount", netAmount,
                    "transactionId", transactionId));

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