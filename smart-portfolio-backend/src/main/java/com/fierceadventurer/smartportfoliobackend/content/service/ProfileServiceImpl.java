package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileResponse;
import com.fierceadventurer.smartportfoliobackend.content.entity.BioCache;
import com.fierceadventurer.smartportfoliobackend.content.entity.Profile;
import com.fierceadventurer.smartportfoliobackend.content.mapper.ProfileMapper;
import com.fierceadventurer.smartportfoliobackend.content.repository.BioCacheRepository;
import com.fierceadventurer.smartportfoliobackend.content.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;
    private final BioCacheRepository bioCacheRepository;
    private final ProfileMapper profileMapper;
    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final ProjectService projectService;
    private final ExperienceService experienceService;

    @Override
    @Cacheable(value = "portfolio_profile")
    public ProfileResponse getProfile() {
        log.info("Fetching profile from database (cache miss)");
        Profile profile = profileRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    Profile defaultProfile = Profile.builder()
                            .name("Ayush Tripathi")
                            .role("Backend Developer")
                            .education("B.Tech in CSE @ Gurukul Kangri Deemed to be University")
                            .bio(null)
                            .email("ayush.tripathi0509@gmail.com")
                            .phone("+91 8178410657")
                            .githubUrl("https://github.com/fierce-adventurer")
                            .linkedinUrl("https://www.linkedin.com/in/ayush-tripathi-89265923a/")
                            .leetcodeUrl("https://leetcode.com/u/ayushtripathi2005/")
                            .codeforcesUrl("https://codeforces.com/profile/fierce-adventurer")
                            .resumeUrl("https://drive.google.com/file/d/1mkDrr9xYSplGzt3btQkSZnR-FzJvXJgq/view?usp=drive_link")
                            .build();
                    return profileRepository.save(defaultProfile);
                });

        if (profile.getBio() == null || profile.getBio().isBlank()) {
            return generateBioWithAi();
        }

        return profileMapper.toResponse(profile);
    }

    @Override
    @Transactional
    @CacheEvict(value = "portfolio_profile", allEntries = true)
    public ProfileResponse updateProfile(ProfileRequest request) {
        log.info("Updating portfolio profile");
        Profile profile = profileRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> profileMapper.toEntity(request));

        profileMapper.updateEntityFromRequest(request, profile);
        Profile saved = profileRepository.save(profile);

        if (saved.getBio() != null && !saved.getBio().isBlank()) {
            saveBioToCacheAndEvictLru(saved.getBio(), saved);
        }

        return profileMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "portfolio_profile", allEntries = true)
    public ProfileResponse generateBioWithAi() {
        log.info("Generating bio dynamically with Groq LLM (Llama-3.3-70b-versatile)...");
        Profile profile = profileRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> Profile.builder()
                        .name("Ayush Tripathi")
                        .role("Backend Developer")
                        .build());

        String prompt = """
                You are a senior technical writer crafting a high-impact terminal portfolio bio for Ayush Tripathi.
                Role: %s
                Education: %s
                Technical Focus: Scalable backend engineering, Java 21, Spring Boot, Microservices, Apache Kafka, Docker, and PostgreSQL.
                
                Write a concise, compelling 2-3 sentence terminal bio summarizing his engineering mindset, core tech stack, and drive to build robust distributed architectures.
                Return ONLY the raw bio text. Do NOT include greetings, quotation marks, markdown code fences, or any other commentary.
                """.formatted(
                        profile.getRole() != null ? profile.getRole() : "Backend Developer",
                        profile.getEducation() != null ? profile.getEducation() : "B.Tech in CSE"
                );

        try {
            ChatClient chatClient = chatClientBuilder.build();
            String generatedBio = chatClient.prompt().user(prompt).call().content();
            if (generatedBio != null && !generatedBio.isBlank()) {
                String cleanBio = generatedBio.trim()
                        .replaceAll("^\"|\"$", "")
                        .replace("```", "")
                        .trim();
                profile.setBio(cleanBio);
                profile = profileRepository.save(profile);
                saveBioToCacheAndEvictLru(cleanBio, profile);
                log.info("Successfully generated and saved new dynamic bio via LLM.");
            }
        } catch (Exception e) {
            log.error("Failed to generate bio via LLM, keeping current bio", e);
        }

        return profileMapper.toResponse(profile);
    }

    @Override
    public Map<String, String> getDynamicBioFromVectorStore() {
        log.info("Processing dynamic bio request with LRU cache pool & Groq Llama-3.3 vector synthesis");

        Profile profile = profileRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> Profile.builder()
                        .name("Ayush Tripathi")
                        .role("Backend Developer")
                        .education("B.Tech in CSE @ Gurukul Kangri Deemed to be University")
                        .build());

        List<BioCache> cachedBios = bioCacheRepository.findAllByOrderByCreatedAtAsc();
        String bioToServe;

        if (!cachedBios.isEmpty()) {
            // Pick n - 2 if available, else latest
            BioCache selected;
            if (cachedBios.size() >= 2) {
                selected = cachedBios.get(cachedBios.size() - 2);
            } else {
                selected = cachedBios.get(cachedBios.size() - 1);
            }

            // Update LRU access timestamp
            selected.setLastAccessedAt(LocalDateTime.now());
            bioCacheRepository.save(selected);
            bioToServe = selected.getBio();

            // Asynchronously generate fresh bio (n - 1) via vector store + Groq Llama-3.3
            CompletableFuture.runAsync(() -> generateAndStoreNewBio(profile));

        } else {
            // Cold start: generate synchronously, save to LRU cache, and serve
            bioToServe = generateAndStoreNewBio(profile);
            if (bioToServe == null || bioToServe.isBlank()) {
                bioToServe = profile.getBio() != null && !profile.getBio().isBlank()
                        ? profile.getBio()
                        : "Backend engineer focused on scalable architectures, Java 21, Spring Boot, distributed microservices, and high-throughput systems with Apache Kafka.";
                saveBioToCacheAndEvictLru(bioToServe, profile);
            }
        }

        return Map.of(
                "bio", bioToServe,
                "name", profile.getName() != null ? profile.getName() : "Ayush Tripathi",
                "role", profile.getRole() != null ? profile.getRole() : "Backend Developer",
                "education", profile.getEducation() != null ? profile.getEducation() : "B.Tech in CSE @ Gurukul Kangri Deemed to be University"
        );
    }

    private String generateAndStoreNewBio(Profile profile) {
        log.info("Synthesizing fresh dynamic bio via vector store + Groq Llama-3.3-70b-versatile...");

        // 1. Query vectorStore for tech stack, skills, recent experiences, unique achievements
        String query = "Ayush Tripathi tech stack skills most recent experience unique achievements distributed systems Spring Boot Java Kafka Docker PostgreSQL microservices";
        List<Document> similarDocuments = Collections.emptyList();
        try {
            similarDocuments = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(4).build()
            );
        } catch (Exception e) {
            log.warn("Vector store query failed or empty: {}", e.getMessage());
        }

        String resumeContext = similarDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // 2. Query projects
        String projectsContext = "";
        try {
            projectsContext = projectService.getAllProjects().stream()
                    .limit(4)
                    .map(p -> String.format("- %s: %s (Tech: %s)", p.title(), p.description(), p.techStack()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Could not retrieve projects for dynamic bio: {}", e.getMessage());
        }

        // 3. Query experiences
        String experiencesContext = "";
        try {
            experiencesContext = experienceService.getAllExperiences().stream()
                    .map(e -> String.format("- %s at %s (%s): %s (Tech: %s)", e.role(), e.company(), e.period(), e.description(), e.techStack()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Could not retrieve experiences for dynamic bio: {}", e.getMessage());
        }

        // 4. Synthesize prompt for Groq Llama-3.3-70b-versatile
        String systemPrompt = String.format("""
                You are an expert technical bio synthesizer for Ayush Tripathi's interactive developer portfolio terminal.
                Synthesize a fresh, dynamic, technically precise, and punchy 2-3 sentence terminal bio summarizing his engineering profile.
                Highlight his core tech stack (Java 21, Spring Boot, Microservices, Apache Kafka, Docker, PostgreSQL), most recent work/internship experiences, skills, and unique technical achievements.

                CANDIDATE PROFILE:
                - Name: %s
                - Role: %s
                - Education: %s

                SEMANTIC RESUME CHUNKS (from pgvector):
                %s

                DEPLOYED PROJECTS:
                %s

                EXPERIENCE TIMELINE:
                %s

                CRITICAL GUIDELINES:
                - Highlight real engineering capabilities (e.g., scalable backend systems, high-concurrency event-driven architectures, resilient distributed microservices, low-latency APIs).
                - Highlight technical skills and unique accomplishments.
                - Conclude with his forward-looking commitment to engineering robust backend solutions.
                - Keep it concise, punchy, professional, and formatted for a Unix terminal.
                - Return ONLY the raw bio text. DO NOT include quotation marks, markdown code fences, greetings, or commentary.
                """,
                profile.getName() != null ? profile.getName() : "Ayush Tripathi",
                profile.getRole() != null ? profile.getRole() : "Backend Developer",
                profile.getEducation() != null ? profile.getEducation() : "B.Tech in CSE @ Gurukul Kangri Deemed to be University",
                !resumeContext.isBlank() ? resumeContext : "Experienced backend engineer specializing in Java, Spring Boot, Kafka, and Docker.",
                !projectsContext.isBlank() ? projectsContext : "Active distributed systems and backend modules.",
                !experiencesContext.isBlank() ? experiencesContext : "Backend development and systems engineering."
        );

        String dynamicBio = null;
        try {
            ChatClient chatClient = chatClientBuilder.build();
            dynamicBio = chatClient.prompt()
                    .system(systemPrompt)
                    .user("Synthesize a fresh, high-impact dynamic terminal bio using recent achievements and tech stack.")
                    .call()
                    .content();

            if (dynamicBio != null && !dynamicBio.isBlank()) {
                dynamicBio = dynamicBio.trim()
                        .replaceAll("^\"|\"$", "")
                        .replace("```", "")
                        .trim();

                saveBioToCacheAndEvictLru(dynamicBio, profile);
                log.info("Successfully generated and saved new dynamic bio (n-1) to LRU cache: {}", dynamicBio);
            }
        } catch (Exception e) {
            log.error("Failed to generate dynamic bio via Groq Llama-3.3: {}", e.getMessage());
        }

        return dynamicBio;
    }

    private synchronized void saveBioToCacheAndEvictLru(String bioText, Profile profile) {
        if (bioText == null || bioText.isBlank()) return;

        LocalDateTime now = LocalDateTime.now();
        BioCache newEntry = BioCache.builder()
                .bio(bioText)
                .lastAccessedAt(now)
                .build();
        bioCacheRepository.save(newEntry);

        // Update profile in DB as well
        profile.setBio(bioText);
        profileRepository.save(profile);

        // Enforce LRU: keep at most 20 bios
        List<BioCache> allLru = bioCacheRepository.findAllByOrderByLastAccessedAtAsc();
        int excess = allLru.size() - 20;
        if (excess > 0) {
            for (int i = 0; i < excess; i++) {
                bioCacheRepository.delete(allLru.get(i));
            }
            log.info("Evicted {} old bios from LRU cache, maintaining pool of 20", excess);
        }
    }
}
