package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fierceadventurer.smartportfoliobackend.content.dto.ProjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ProjectServiceImpl implements ProjectService {

    private final RestClient restClient;
    private final String githubUsername;

    public ProjectServiceImpl(
            @Value("${portfolio.github.username:fierce-adventurer}") String githubUsername,
            @Value("${portfolio.github.token:}") String githubToken) {
        this.githubUsername = githubUsername;

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "Smart-Portfolio-Backend");

        if (githubToken != null && !githubToken.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken.trim());
        }

        this.restClient = builder.build();
    }

    @Override
    @Cacheable(value = "portfolio_projects")
    public List<ProjectResponse> getAllProjects() {
        log.info("Fetching public projects directly from GitHub API for user: {} (cache miss)", githubUsername);
        return fetchRepositoriesFromGitHub();
    }

    @Override
    @CacheEvict(value = "portfolio_projects", allEntries = true)
    public List<ProjectResponse> syncProjectsFromGitHub() {
        log.info("Manually syncing public projects from GitHub API for user: {}", githubUsername);
        return fetchRepositoriesFromGitHub();
    }

    private List<ProjectResponse> fetchRepositoriesFromGitHub() {
        try {
            JsonNode reposNode = restClient.get()
                    .uri("/users/{username}/repos?sort=updated&per_page=100", githubUsername)
                    .retrieve()
                    .body(JsonNode.class);

            if (reposNode == null || !reposNode.isArray()) {
                log.warn("GitHub API returned non-array response for user: {}", githubUsername);
                return Collections.emptyList();
            }

            List<ProjectResponse> projects = new ArrayList<>();

            for (JsonNode repo : reposNode) {
                // Skip forks and the user profile readme repo
                boolean isFork = repo.path("fork").asBoolean(false);
                String repoName = repo.path("name").asText("");
                if (isFork || repoName.equalsIgnoreCase(githubUsername) || repoName.startsWith(".")) {
                    continue;
                }

                String description = repo.path("description").asText("");
                if (description == null || description.isBlank() || description.equalsIgnoreCase("null")) {
                    description = "Open-source software project engineered by " + githubUsername + ".";
                }

                String language = repo.path("language").asText("");
                List<String> techList = new ArrayList<>();
                if (!language.isBlank() && !language.equalsIgnoreCase("null")) {
                    techList.add(language);
                }

                JsonNode topicsNode = repo.path("topics");
                if (topicsNode.isArray()) {
                    for (JsonNode t : topicsNode) {
                        String topic = t.asText().trim();
                        if (!topic.isBlank() && !techList.contains(topic)) {
                            techList.add(topic);
                        }
                    }
                }

                String techStack = techList.isEmpty() ? "Software Engineering" : String.join(", ", techList);
                String htmlUrl = repo.path("html_url").asText("");
                String homepage = repo.path("homepage").asText("");
                String liveUrl = (homepage != null && !homepage.isBlank() && !homepage.equalsIgnoreCase("null")) ? homepage : null;

                UUID id = UUID.nameUUIDFromBytes(repoName.getBytes(StandardCharsets.UTF_8));

                projects.add(new ProjectResponse(
                        id,
                        repoName,
                        description,
                        techStack,
                        htmlUrl,
                        liveUrl
                ));
            }

            log.info("Successfully fetched and mapped {} public repositories from GitHub API.", projects.size());
            return projects;

        } catch (Exception e) {
            log.error("Failed to fetch repositories from GitHub API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
