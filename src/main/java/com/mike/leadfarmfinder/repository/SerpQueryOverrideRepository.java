package com.mike.leadfarmfinder.repository;

import com.mike.leadfarmfinder.entity.SerpQueryOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SerpQueryOverrideRepository extends JpaRepository<SerpQueryOverride, Long> {

    List<SerpQueryOverride> findByActiveTrue();

    Optional<SerpQueryOverride> findByOriginalQueryAndActiveTrue(String originalQuery);
}