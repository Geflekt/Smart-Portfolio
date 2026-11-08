package com.fierceadventurer.smartportfoliobackend.content.dto;

import jakarta.validation.constraints.NotBlank;

public record ExperienceRequest(
    @NotBlank String role,
    @NotBlank String company,
    String period,
    String description,
    String techStack,
    Integer sortOrder
) {}
