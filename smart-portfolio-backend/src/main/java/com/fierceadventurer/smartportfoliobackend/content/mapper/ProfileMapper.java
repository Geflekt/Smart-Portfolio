package com.fierceadventurer.smartportfoliobackend.content.mapper;

import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileResponse;
import com.fierceadventurer.smartportfoliobackend.content.entity.Profile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProfileMapper {
    ProfileResponse toResponse(Profile profile);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Profile toEntity(ProfileRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(ProfileRequest request, @MappingTarget Profile profile);
}
