package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileResponse;

import java.util.Map;

public interface ProfileService {
    ProfileResponse getProfile();
    ProfileResponse updateProfile(ProfileRequest request);
    ProfileResponse generateBioWithAi();
    Map<String, String> getDynamicBioFromVectorStore();
}
