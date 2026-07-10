package com.sellerprofit.contact;

import com.sellerprofit.contact.dto.ContactRequest;
import com.sellerprofit.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final EmailService emailService;
    private final String contactTo;

    public ContactService(EmailService emailService,
                          @Value("${app.mail.contact-to:${app.mail.from}}") String contactTo) {
        this.emailService = emailService;
        this.contactTo = contactTo;
    }

    public void send(ContactRequest request, String clientIp) {
        String subject = "[seller-profit contact] " + clean(request.subject());
        String text = """
                New seller-profit inquiry.

                Name: %s
                Email: %s
                Subject: %s
                ReceivedAt: %s
                IP: %s
                Context: %s

                Message:
                %s
                """.formatted(
                clean(request.name()),
                clean(request.email()),
                clean(request.subject()),
                ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                clientIp,
                blankToDash(request.context()),
                request.message().trim()
        );

        log.info("[contact] sending inquiry email to={} from={} subject={}",
                contactTo, clean(request.email()), clean(request.subject()));
        emailService.send(contactTo, subject, text, request.email().trim());
        log.info("[contact] inquiry email sent to={} from={}", contactTo, clean(request.email()));
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : clean(value);
    }
}
