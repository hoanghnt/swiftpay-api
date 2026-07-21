package com.hoanghnt.swiftpay.service;

import com.hoanghnt.swiftpay.config.EmailAsyncConfig;
import jakarta.annotation.PostConstruct;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.from:}")
    private String fromEmail;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    private boolean deliveryEnabled;

    @PostConstruct
    void resolveDeliveryMode() {
        boolean mailConfigured = StringUtils.hasText(mailHost) && StringUtils.hasText(fromEmail);
        deliveryEnabled = notificationsEnabled && mailConfigured;

        if (!notificationsEnabled) {
            log.info("Email: TẮT qua app.notifications.enabled=false.");
        } else if (!mailConfigured) {
            log.warn("Email: TẮT vì chưa cấu hình mail (spring.mail.host='{}', spring.mail.from='{}'). "
                    + "Đặt MAIL_HOST/MAIL_FROM để bật gửi email.", mailHost, fromEmail);
        } else {
            log.info("Email: BẬT, gửi qua host={} from={}", mailHost, fromEmail);
        }
    }

    @Async(EmailAsyncConfig.EMAIL_EXECUTOR)
    public void sendTransferSentEmail(String toEmail, String senderUsername,
            String receiverUsername, BigDecimal amount, UUID transactionId) {
        send(toEmail, "SwiftPay: Bạn vừa chuyển tiền thành công", "transaction-sent",
                Map.of("senderUsername", senderUsername,
                        "receiverUsername", receiverUsername,
                        "amount", amount,
                        "transactionId", transactionId));
    }

    @Async(EmailAsyncConfig.EMAIL_EXECUTOR)
    public void sendTransferReceivedEmail(String toEmail, String receiverUsername,
            String senderUsername, BigDecimal amount, UUID transactionId) {
        send(toEmail, "SwiftPay: Bạn vừa nhận được tiền", "transaction-received",
                Map.of("receiverUsername", receiverUsername,
                        "senderUsername", senderUsername,
                        "amount", amount,
                        "transactionId", transactionId));
    }

    @Async(EmailAsyncConfig.EMAIL_EXECUTOR)
    public void sendTopupSuccessEmail(String toEmail, String username,
            BigDecimal amount, UUID transactionId) {
        send(toEmail, "SwiftPay: Nạp tiền thành công", "transaction-topup",
                Map.of("username", username,
                        "amount", amount,
                        "transactionId", transactionId));
    }

    private void send(String toEmail, String subject, String template, Map<String, Object> vars) {
        if (!deliveryEnabled) {
            return;
        }
        try {
            Context context = new Context();
            vars.forEach(context::setVariable);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(templateEngine.process(template, context), true);

            mailSender.send(message);
            log.info("Đã gửi email '{}' tới {}", template, toEmail);
        } catch (Exception e) {
            log.error("Gửi email '{}' tới {} thất bại — giao dịch KHÔNG bị ảnh hưởng", template, toEmail, e);
        }
    }
}
