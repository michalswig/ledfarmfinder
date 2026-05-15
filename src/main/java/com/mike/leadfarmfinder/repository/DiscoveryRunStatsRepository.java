package com.mike.leadfarmfinder.repository;

import com.mike.leadfarmfinder.entity.DiscoveryRunStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscoveryRunStatsRepository extends JpaRepository<DiscoveryRunStats, Long> {

    @Query("""
            SELECT new com.mike.leadfarmfinder.repository.SerpQueryStatsProjection(
                COUNT(s),
                COALESCE(SUM(s.acceptedUrls), 0),
                COALESCE(SUM(s.rejectedUrls), 0),
                COALESCE(SUM(s.pagesVisited), 0),
                COALESCE(SUM(s.rawUrls), 0)
            )
            FROM DiscoveryRunStats s
            WHERE s.query = :query
            """)
    SerpQueryStatsProjection aggregateByQuery(@Param("query") String query);
}