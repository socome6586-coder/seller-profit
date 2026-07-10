package com.sellerprofit.contact;

import com.sellerprofit.contact.dto.ContactRequest;
import com.sellerprofit.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactService contactService;
    private final RateLimiter rateLimiter;

    public ContactController(ContactService contactService, RateLimiter rateLimiter) {
        this.contactService = contactService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@Valid @RequestBody ContactRequest request, HttpServletRequest http) {
        String ip = rateLimiter.clientIp(http);
        rateLimiter.check("contact-ip:" + ip, 5, Duration.ofMinutes(10));
        rateLimiter.check("contact-email:" + request.email().trim().toLowerCase(), 3, Duration.ofMinutes(10));
        contactService.send(request, ip);
    }
}
