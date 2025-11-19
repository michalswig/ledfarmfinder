package com.mike.leadfarmfinder.repository;

import com.mike.leadfarmfinder.entity.FarmSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FarmSourceRepository extends JpaRepository<FarmSource, Long> {

    Optional<FarmSource> findByDomain(String domain);
}
