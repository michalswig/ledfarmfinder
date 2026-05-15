package com.mike.leadfarmfinder.repository;

import com.mike.leadfarmfinder.entity.SerpQueryScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SerpQueryScoreRepository extends JpaRepository<SerpQueryScore, Long> {

    Optional<SerpQueryScore> findByQuery(String query);

    List<SerpQueryScore> findByScoreLessThan(int score);
}