package com.mike.leadfarmfinder.controller;

import com.mike.leadfarmfinder.entity.DiscoveryRunStats;
import com.mike.leadfarmfinder.repository.DiscoveryRunStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminStatsController {

    private final DiscoveryRunStatsRepository discoveryRunStatsRepository;

    @GetMapping("/api/admin/discovery-runs/latest")
    public List<DiscoveryRunStats> getLatestRuns(
            @RequestParam(defaultValue = "20") int limit
    ) {
        var pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startedAt"));
        return discoveryRunStatsRepository.findAll(pageable).getContent();
    }
}
