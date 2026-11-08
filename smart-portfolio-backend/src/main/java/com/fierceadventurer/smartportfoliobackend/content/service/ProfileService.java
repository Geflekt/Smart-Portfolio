package com.fierceadventurer.smartportfoliobackend.content.service;

import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileResponse;

public interface ProfileService {
    ProfileResponse getProfile();
    ProfileResponse updateProfile(ProfileRequest request);
}
