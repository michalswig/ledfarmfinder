package com.mike.leadfarmfinder.repository;

import com.mike.leadfarmfinder.entity.AjbCursor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AjbCursorRepository extends JpaRepository<AjbCursor, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AjbCursor c where c.id = :id")
    Optional<AjbCursor> findByIdForUpdate(@Param("id") Integer id);
}

