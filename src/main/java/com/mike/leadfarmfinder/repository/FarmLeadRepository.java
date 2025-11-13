package com.mike.leadfarmfinder.repository;

import com.mike.leadfarmfinder.entity.FarmLead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FarmLeadRepository extends JpaRepository<FarmLead,Integer> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<FarmLead> findByEmailIgnoreCase(String email);
}
