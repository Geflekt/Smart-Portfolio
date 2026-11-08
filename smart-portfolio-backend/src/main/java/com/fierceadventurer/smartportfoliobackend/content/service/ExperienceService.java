package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fierceadventurer.smartportfoliobackend.content.dto.ExperienceRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ExperienceResponse;

import java.util.List;
import java.util.UUID;

public interface ExperienceService {
    List<ExperienceResponse> getAllExperiences();
    ExperienceResponse createExperience(ExperienceRequest request);
    boolean deleteExperience(UUID id);
}
