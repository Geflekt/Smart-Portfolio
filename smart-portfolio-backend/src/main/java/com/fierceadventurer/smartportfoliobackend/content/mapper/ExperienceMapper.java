package com.fierceadventurer.smartportfoliobackend.content.mapper;

import com.fierceadventurer.smartportfoliobackend.content.dto.ExperienceRequest;
import com.fierceadventurer.smartportfoliobackend.content.dto.ExperienceResponse;
import com.fierceadventurer.smartportfoliobackend.content.entity.Experience;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ExperienceMapper {
    ExperienceResponse toResponse(Experience experience);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Experience toEntity(ExperienceRequest request);
}
