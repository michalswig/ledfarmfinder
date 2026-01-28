package com.mike.leadfarmfinder.repository;

import com.mike.leadfarmfinder.entity.DiscoveredUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiscoveredUrlRepository extends JpaRepository<DiscoveredUrl, Long> {

    boolean existsByUrl(String url);
    boolean existsByDomain(String domain);
    Optional<DiscoveredUrl> findByUrl(String url);
}
