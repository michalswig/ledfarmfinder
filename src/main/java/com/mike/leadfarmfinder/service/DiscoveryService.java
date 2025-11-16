package com.mike.leadfarmfinder.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiscoveryService {

    public List<String> findCandidateFarmUrls(int limit) {

        List<String> urlLeads = List.of(
                "https://www.enderhof.de/"
        );

        if (urlLeads.size() >= limit) {
            return urlLeads.subList(0, limit);
        }
        return urlLeads;
    }

}
