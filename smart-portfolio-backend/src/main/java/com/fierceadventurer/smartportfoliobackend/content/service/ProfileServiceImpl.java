package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileResponse;
import com.fierceadventurer.smartportfoliobackend.content.entity.BioCache;
import com.fierceadventurer.smartportfoliobackend.content.repository.BioCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final BioCacheRepository bioCacheRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final ProjectService projectService;
    private final ExperienceService experienceService;

    private static final String DEFAULT_NAME = "Ayush Tripathi";
    private static final String DEFAULT_ROLE = "Backend Developer";
    private static final String DEFAULT_EDUCATION = "B.Tech in CSE @ Gurukul Kangri Deemed to be University";
    private static final String DEFAULT_BIO = "Backend Engineer focused on scalable architectures, Java 21, Spring Boot, distributed microservices, and Apache Kafka.";
    private static final String DEFAULT_EMAIL = "ayush.tripathi0509@gmail.com";
    private static final String DEFAULT_PHONE = "+91 8178410657";
    private static final String DEFAULT_GITHUB = "https://github.com/fierce-adventurer";
    private static final String DEFAULT_LINKEDIN = "https://www.linkedin.com/in/ayush-tripathi-89265923a/";
    private static final String DEFAULT_LEETCODE = "https://leetcode.com/u/ayushtripathi2005/";
    private static final String DEFAULT_CODEFORCES = "https://codeforces.com/profile/fierce-adventurer";
    private static final String DEFAULT_RESUME = "https://drive.google.com/file/d/1mkDrr9xYSplGzt3btQkSZnR-FzJvXJgq/view?usp=drive_link";

    @Override
    @Cacheable(value = "portfolio_profile")
    public ProfileResponse getProfile() {
        log.info("Fetching profile details (cache miss)");
        String bio = getLatestBioFromCache();
        return new ProfileResponse(
                null,
                DEFAULT_NAME,
                DEFAULT_ROLE,
                DEFAULT_EDUCATION,
                bio,
                DEFAULT_EMAIL,
                DEFAULT_PHONE,
                DEFAULT_GITHUB,
                DEFAULT_LINKEDIN,
                DEFAULT_LEETCODE,
                DEFAULT_CODEFORCES,
                DEFAULT_RESUME
        );
    }

    private String getLatestBioFromCache() {
        List<BioCache> cached = bioCacheRepository.findAllByOrderByLastAccessedAtAsc();
        if (!cached.isEmpty()) {
            return cached.get(cached.size() - 1).getBio();
        }
        return DEFAULT_BIO;
    }

    @Override
    public Map<String, String> getDynamicBioFromVectorStore() {
        log.info("Processing dynamic bio request with LRU cache pool & Groq LLM vector synthesis");

        List<BioCache> cachedBios = bioCacheRepository.findAllByOrderByCreatedAtAsc();

        if (!cachedBios.isEmpty()) {
            int targetIndex = Math.max(0, cachedBios.size() - 2);
            BioCache servedBio = cachedBios.get(targetIndex);

            servedBio.setLastAccessedAt(LocalDateTime.now());
            bioCacheRepository.save(servedBio);

            log.info("Instantly served cached bio [index {} of {}]: \"{}\"",
                    targetIndex, cachedBios.size(), servedBio.getBio());

            // Fire and forget async generation of the next bio (n-1) to keep the LRU pool fresh
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Kicking off background vector search and Groq LLM bio synthesis...");
                    synthesizeAndCacheNextBio();
                } catch (Exception e) {
                    log.error("Asynchronous bio generation encountered an error: {}", e.getMessage(), e);
                }
            });

            return Map.of("bio", servedBio.getBio());
        }

        // Cache miss: synthesize immediately, store in bio_cache, and return
        log.info("Bio cache is completely empty. Synthesizing initial dynamic bio synchronously...");
        String synthesizedBio = synthesizeAndCacheNextBio();

        if (synthesizedBio != null && !synthesizedBio.isBlank()) {
            return Map.of("bio", synthesizedBio);
        }

        return Map.of("bio", DEFAULT_BIO);
    }

    private String synthesizeAndCacheNextBio() {
        String semanticQuery = "backend distributed systems Java Spring Boot Kafka Docker microservices architectural achievements";
        String resumeContext = "";
        try {
            List<Document> relevantDocs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(semanticQuery).topK(4).build()
            );
            resumeContext = relevantDocs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n---\n"));
            log.info("Retrieved {} relevant knowledge chunks from vector store for bio synthesis.", relevantDocs.size());
        } catch (Exception e) {
            log.warn("Vector store query failed for bio synthesis: {}", e.getMessage());
        }

        String projectsContext = "";
        try {
            var projects = projectService.getAllProjects();
            projectsContext = projects.stream()
                    .map(p -> p.title() + ": " + p.description() + " (Tech: " + p.techStack() + ")")
                    .collect(Collectors.joining("; "));
        } catch (Exception e) {
            log.warn("Could not retrieve projects context for bio synthesis: {}", e.getMessage());
        }

        String experiencesContext = "";
        try {
            var experiences = experienceService.getAllExperiences();
            experiencesContext = experiences.stream()
                    .map(exp -> exp.role() + " @ " + exp.company() + ": " + exp.description() + " (Tech: " + exp.techStack() + ")")
                    .collect(Collectors.joining("; "));
        } catch (Exception e) {
            log.warn("Could not retrieve experiences context for bio synthesis: {}", e.getMessage());
        }

        String systemPrompt = String.format("""
                You are a senior technical writer crafting a dynamic, high-impact terminal portfolio bio for %s.
                Target Role: %s
                Education: %s
                
                SOURCE KNOWLEDGE:
                ---
                Resume & Skills Data:
                %s
                
                Deployed Architecture Projects:
                %s
                
                Work Experiences:
                %s
                ---
                
                INSTRUCTIONS:
                1. Write a punchy, technical 2-3 sentence terminal bio summarizing the candidate's core backend engineering strengths, architectural accomplishments, and key technologies (Java, Spring Boot, Microservices, Kafka, Docker).
                2. Vary the tone and focus slightly for freshness on every load, emphasizing different strengths (e.g. distributed systems reliability, event-driven pipelines, clean API design, containerization).
                3. Return ONLY the raw bio text. Do NOT include headings, quotes, preamble, or markdown backticks.
                """,
                DEFAULT_NAME,
                DEFAULT_ROLE,
                DEFAULT_EDUCATION,
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

                saveBioToCacheAndEvictLru(dynamicBio);
                log.info("Successfully generated and saved new dynamic bio (n-1) to LRU cache: {}", dynamicBio);
            }
        } catch (Exception e) {
            log.error("Failed to generate dynamic bio via Groq LLM: {}", e.getMessage());
        }

        return dynamicBio;
    }

    private synchronized void saveBioToCacheAndEvictLru(String bioText) {
        if (bioText == null || bioText.isBlank()) return;

        LocalDateTime now = LocalDateTime.now();
        BioCache newEntry = BioCache.builder()
                .bio(bioText)
                .lastAccessedAt(now)
                .build();
        bioCacheRepository.save(newEntry);

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
