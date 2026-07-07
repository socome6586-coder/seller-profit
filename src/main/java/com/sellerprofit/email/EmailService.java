package com.sellerprofit.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 메일 발송. 토스 빌링({@code TossBillingClient})과 같은 플레이스홀더-세이프 패턴 —
 * SMTP 계정({@code spring.mail.username})이 비어 있으면(로컬/미설정 상태) 실제 발송 대신
 * 로그로만 남긴다. 이렇게 하면 SMTP 자격증명 없이도 안전하게 빌드/배포되고, 나중에
 * 환경변수(MAIL_USERNAME/MAIL_PASSWORD)만 채우면 바로 실제 발송으로 전환된다.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean configured;

    public EmailService(JavaMailSender mailSender,
                         @Value("${spring.mail.username:}") String username,
                         @Value("${app.mail.from:${spring.mail.username:noreply@sellerprofit.co.kr}}") String from) {
        this.mailSender = mailSender;
        this.from = from;
        this.configured = username != null && !username.isBlank();
    }

    /** 실 SMTP 계정이 주입됐는지. false 면 send() 가 실제 발송 대신 로그로만 남긴다. */
    public boolean isConfigured() {
        return configured;
    }

    public void send(String to, String subject, String text) {
        if (!configured) {
            log.warn("[email] SMTP 미설정(MAIL_USERNAME 없음) — 실제 발송 대신 로그로 남김.\nto={}\nsubject={}\n{}",
                    to, subject, text);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
