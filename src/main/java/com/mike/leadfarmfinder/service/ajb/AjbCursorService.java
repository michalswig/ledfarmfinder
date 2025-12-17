package com.mike.leadfarmfinder.service.ajb;

import com.mike.leadfarmfinder.entity.AjbCursor;
import com.mike.leadfarmfinder.repository.AjbCursorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AjbCursorService {

    private static final int CURSOR_ID = 1;

    private final AjbCursorRepository repo;

    @Transactional
    public int allocateStartPage(int pagesPerRun, int maxPageCapExclusive) {
        if (pagesPerRun <= 0) throw new IllegalArgumentException("pagesPerRun must be > 0");
        if (maxPageCapExclusive <= 0) throw new IllegalArgumentException("maxPageCapExclusive must be > 0");

        AjbCursor c = repo.findByIdForUpdate(CURSOR_ID)
                .orElseGet(() -> {
                    AjbCursor created = new AjbCursor();
                    created.setId(CURSOR_ID);
                    created.setNextPage(0);
                    created.setUpdatedAt(LocalDateTime.now());
                    // ✅ ważne: jeśli nie masz inserta w Liquibase, zapisz rekord
                    return repo.save(created);
                });

        int current = c.getNextPage();
        if (current >= maxPageCapExclusive) current = 0;

        int startPage = current;
        int next = current + pagesPerRun;

        if (next >= maxPageCapExclusive) next = 0;

        c.setNextPage(next);
        c.setUpdatedAt(LocalDateTime.now());
        repo.save(c);

        log.info("AJB CURSOR: allocated startPage={}, pagesPerRun={}, nextPageAfter={}, cap={}",
                startPage, pagesPerRun, next, maxPageCapExclusive);

        return startPage;
    }
}
