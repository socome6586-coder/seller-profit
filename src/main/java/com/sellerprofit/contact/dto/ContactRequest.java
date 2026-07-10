package com.sellerprofit.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 120) String subject,
        @NotBlank @Size(max = 3000) String message,
        @Size(max = 200) String context,
        @Size(max = 200) String website
) {
}
