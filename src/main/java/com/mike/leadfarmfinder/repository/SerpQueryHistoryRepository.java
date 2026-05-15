package com.mike.leadfarmfinder.repository;

import com.mike.leadfarmfinder.entity.SerpQueryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SerpQueryHistoryRepository extends JpaRepository<SerpQueryHistory, Long> {

    List<SerpQueryHistory> findByReplacedQueryOrderByCreatedAtDesc(String replacedQuery);
}