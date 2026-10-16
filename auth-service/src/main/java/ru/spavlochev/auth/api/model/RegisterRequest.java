package ru.spavlochev.auth.api.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Pattern(regexp = "^[+]?\\d{10,15}$", message = "Invalid phone number format") String phone) {
}
