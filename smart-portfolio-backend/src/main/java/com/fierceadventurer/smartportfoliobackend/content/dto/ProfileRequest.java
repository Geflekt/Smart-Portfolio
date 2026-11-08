package com.fierceadventurer.smartportfoliobackend.content.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileRequest(
    @NotBlank String name,
    String role,
    String education,
    String bio,
    String email,
    String phone,
    String githubUrl,
    String linkedinUrl,
    String leetcodeUrl,
    String codeforcesUrl,
    String resumeUrl
) {}
