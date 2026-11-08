package com.fierceadventurer.smartportfoliobackend.content.controller;

import com.fierceadventurer.smartportfoliobackend.content.dto.ExperienceRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ExperienceResponse;
import com.fierceadventurer.smartportfoliobackend.content.service.ExperienceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/experiences")
@RequiredArgsConstructor
public class AdminExperienceController {

    private final ExperienceService experienceService;

    @PostMapping
    public ResponseEntity<ExperienceResponse> createExperience(@Valid @RequestBody ExperienceRequest request) {
        ExperienceResponse response = experienceService.createExperience(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteExperience(@PathVariable UUID id) {
        log.info("Admin deleting experience ID: {}", id);
        boolean deleted = experienceService.deleteExperience(id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Experience not found.");
        }
        return ResponseEntity.ok("Experience successfully deleted.");
    }
}
