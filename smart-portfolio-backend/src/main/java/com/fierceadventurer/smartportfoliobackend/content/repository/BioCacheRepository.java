package com.fierceadventurer.smartportfoliobackend.content.repository;

import com.fierceadventurer.smartportfoliobackend.content.entity.BioCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BioCacheRepository extends JpaRepository<BioCache, UUID> {
    List<BioCache> findAllByOrderByCreatedAtAsc();
    List<BioCache> findAllByOrderByLastAccessedAtAsc();
}
