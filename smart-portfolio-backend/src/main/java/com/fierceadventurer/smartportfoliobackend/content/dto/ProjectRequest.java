package com.fierceadventurer.smartportfoliobackend.content.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectRequest(
        @NotBlank(message = "Title is mandatory")
        String title,

        @NotBlank(message = "Description is mandatory")
        String description,

        String techStack,
        String githubUrl,
        String liveUrl
) {}
