package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileResponse;

import java.util.Map;

public interface ProfileService {
    ProfileResponse getProfile();
    Map<String, String> getDynamicBioFromVectorStore();
}
