package com.mike.leadfarmfinder.repository;

import com.mike.leadfarmfinder.entity.SerpQueryCursor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SerpQueryCursorRepository extends JpaRepository<SerpQueryCursor, Long> {

    Optional<SerpQueryCursor> findByQuery(String query);
}
