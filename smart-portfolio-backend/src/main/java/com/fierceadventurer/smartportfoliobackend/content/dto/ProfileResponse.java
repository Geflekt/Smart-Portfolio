package com.fierceadventurer.smartportfoliobackend.content.dto;

import java.util.UUID;

public record ProfileResponse(
    UUID id,
    String name,
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
