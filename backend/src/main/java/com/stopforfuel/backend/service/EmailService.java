package com.stopforfuel.backend.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

/**
 * Thin wrapper over {@link JavaMailSender}. No-op when {@code stopforfuel.mail.enabled=false}
 * or when no mail sender bean is available — mirrors the guard pattern used by
 * {@link PushNotificationService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final Optional<JavaMailSender> mailSender;

    @Value("${stopforfuel.mail.enabled:false}")
    private boolean enabled;

    @Value("${stopforfuel.mail.from:no-reply@stopforfuel.com}")
    private String from;

    public void sendWithAttachment(Collection<String> to, String subject, String body,
                                   String filename, byte[] pdfBytes) {
        if (!enabled || mailSender.isEmpty()) {
            log.debug("Mail disabled — skipping email to {}", to);
            return;
        }
        if (to == null || to.isEmpty()) return;
        try {
            MimeMessage msg = mailSender.get().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(body, false);
            if (pdfBytes != null && pdfBytes.length > 0 && filename != null) {
                helper.addAttachment(filename, new ByteArrayResource(pdfBytes));
            }
            mailSender.get().send(msg);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
