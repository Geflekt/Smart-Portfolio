package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileResponse;
import com.fierceadventurer.smartportfoliobackend.content.entity.Profile;
import com.fierceadventurer.smartportfoliobackend.content.mapper.ProfileMapper;
import com.fierceadventurer.smartportfoliobackend.content.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;
    private final ChatClient.Builder chatClientBuilder;

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

        return profileMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "portfolio_profile", allEntries = true)
    public ProfileResponse generateBioWithAi() {
        log.info("Generating bio dynamically with Groq LLM (Llama-3)...");
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
                log.info("Successfully generated and saved new dynamic bio via LLM.");
            }
        } catch (Exception e) {
            log.error("Failed to generate bio via LLM, keeping current bio", e);
        }

        return profileMapper.toResponse(profile);
    }
}
