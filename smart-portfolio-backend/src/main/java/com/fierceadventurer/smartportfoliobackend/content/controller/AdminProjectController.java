package com.fierceadventurer.smartportfoliobackend.content.controller;

import com.fierceadventurer.smartportfoliobackend.content.dto.ProjectResponse;
import com.fierceadventurer.smartportfoliobackend.content.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/projects") // Secured by Interceptor!
@RequiredArgsConstructor
public class AdminProjectController {

    private final ProjectService projectService;

    @PostMapping("/sync")
    public ResponseEntity<List<ProjectResponse>> syncProjects() {
        log.info("Admin triggered manual GitHub projects synchronization");
        return ResponseEntity.ok(projectService.syncProjectsFromGitHub());
    }
}
