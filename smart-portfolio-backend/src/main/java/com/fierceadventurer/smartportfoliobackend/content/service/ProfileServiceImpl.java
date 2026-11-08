package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileResponse;
import com.fierceadventurer.smartportfoliobackend.content.entity.Profile;
import com.fierceadventurer.smartportfoliobackend.content.mapper.ProfileMapper;
import com.fierceadventurer.smartportfoliobackend.content.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                            .bio("Experienced in building scalable backend systems and event-driven architectures. My core stack includes Java, Spring Boot, Microservices, Kafka, and Docker.\n\nCurrently seeking hands-on internship opportunities to engineer robust solutions.")
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
}
