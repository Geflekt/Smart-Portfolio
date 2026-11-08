package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fierceadventurer.smartportfoliobackend.content.dto.ExperienceRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ExperienceResponse;
import com.fierceadventurer.smartportfoliobackend.content.entity.Experience;
import com.fierceadventurer.smartportfoliobackend.content.mapper.ExperienceMapper;
import com.fierceadventurer.smartportfoliobackend.content.repository.ExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceServiceImpl implements ExperienceService {

    private final ExperienceRepository experienceRepository;
    private final ExperienceMapper experienceMapper;

    @Override
    @Cacheable(value = "portfolio_experiences")
    public List<ExperienceResponse> getAllExperiences() {
        log.info("Fetching all experiences from database (cache miss)");
        List<Experience> experiences = experienceRepository.findAllByOrderBySortOrderAscCreatedAtDesc();

        if (experiences.isEmpty()) {
            Experience defaultExp = Experience.builder()
                    .role("Virtual Software Engineering Intern")
                    .company("IIT Ropar")
                    .period("Jan 2026")
                    .description("Participated in an intensive 10-week virtual internship focusing on full-stack web development.")
                    .techStack("MongoDB, Express.js, React, Node.js")
                    .sortOrder(1)
                    .build();
            Experience saved = experienceRepository.save(defaultExp);
            experiences = List.of(saved);
        }

        return experiences.stream()
                .map(experienceMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @CacheEvict(value = "portfolio_experiences", allEntries = true)
    public ExperienceResponse createExperience(ExperienceRequest request) {
        log.info("Creating new experience: {} at {}", request.role(), request.company());
        Experience entity = experienceMapper.toEntity(request);
        Experience saved = experienceRepository.save(entity);
        return experienceMapper.toResponse(saved);
    }

    @Override
    @CacheEvict(value = "portfolio_experiences", allEntries = true)
    public boolean deleteExperience(UUID id) {
        log.info("Deleting experience ID: {}", id);
        if (experienceRepository.existsById(id)) {
            experienceRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
