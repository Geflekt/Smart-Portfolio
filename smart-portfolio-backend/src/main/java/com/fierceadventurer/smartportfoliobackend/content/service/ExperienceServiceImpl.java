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

        return experiences.stream()
                .map(experienceMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @CacheEvict(value = "portfolio_experiences", allEntries = true)
    public ExperienceResponse createExperience(ExperienceRequest request) {
        log.info("Creating new experience: {} at {}", request.role(), request.company());

        if (request.startDate() != null && request.endDate() != null
                && !request.startDate().isBlank() && !request.endDate().isBlank()
                && !request.endDate().equalsIgnoreCase("Present")) {
            if (request.startDate().compareTo(request.endDate()) > 0) {
                throw new IllegalArgumentException("Start date cannot be later than end date");
            }
        }

        Experience entity = experienceMapper.toEntity(request);

        if ((entity.getPeriod() == null || entity.getPeriod().isBlank()) && request.startDate() != null && !request.startDate().isBlank()) {
            String end = (request.endDate() != null && !request.endDate().isBlank()) ? request.endDate() : "Present";
            entity.setPeriod(request.startDate() + " - " + end);
        }

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

    @Override
    @CacheEvict(value = "portfolio_experiences", allEntries = true)
    public void deleteAllExperiences() {
        log.info("Deleting all experiences from database");
        experienceRepository.deleteAll();
    }
}
