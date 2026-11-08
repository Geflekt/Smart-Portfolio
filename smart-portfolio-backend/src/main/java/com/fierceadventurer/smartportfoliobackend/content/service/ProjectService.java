package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fierceadventurer.smartportfoliobackend.content.dto.ProjectRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ProjectResponse;

import java.util.List;
import java.util.UUID;

public interface ProjectService {
    List<ProjectResponse> getAllProjects();

    ProjectResponse createProject(ProjectRequest request);

    boolean deleteProject(UUID id);
}
