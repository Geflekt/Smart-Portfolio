package com.fierceadventurer.smartportfoliobackend.content.dto;

import java.util.UUID;

public record ExperienceResponse(
    UUID id,
    String role,
    String company,
    String period,
    String startDate,
    String endDate,
    String description,
    String techStack,
    Integer sortOrder
) {}
