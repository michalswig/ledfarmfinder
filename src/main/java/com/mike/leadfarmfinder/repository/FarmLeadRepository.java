package com.mike.leadfarmfinder.repository;

import com.mike.leadfarmfinder.entity.FarmLead;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FarmLeadRepository extends JpaRepository<FarmLead, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<FarmLead> findByEmailIgnoreCase(String email);

    Optional<FarmLead> findByUnsubscribeToken(String unsubscribeToken);

    Optional<FarmLead> findFirstByActiveTrueAndBounceFalseAndFirstEmailSentAtIsNullOrderByCreatedAtAsc();

    List<FarmLead> findByActiveTrueAndBounceFalseAndFirstEmailSentAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    List<FarmLead> findByActiveTrueAndBounceFalseAndFirstEmailSentAtIsNotNullAndLastEmailSentAtBeforeOrderByLastEmailSentAtAsc(
            LocalDateTime cutoff, Pageable pageable);

    // ✅ NEW (minimal): first email candidates excluding Telekom/t-online
    @Query("""
        select f from FarmLead f
        where f.active = true
          and f.bounce = false
          and f.firstEmailSentAt is null
          and lower(f.email) not like concat('%@', :d1)
          and lower(f.email) not like concat('%@', :d2)
        order by f.createdAt asc
    """)
    List<FarmLead> findFirstEmailCandidatesExcludingDomains(
            @Param("d1") String d1,
            @Param("d2") String d2,
            Pageable pageable
    );

    // ✅ NEW (minimal): follow-up candidates excluding Telekom/t-online
    @Query("""
        select f from FarmLead f
        where f.active = true
          and f.bounce = false
          and f.firstEmailSentAt is not null
          and f.lastEmailSentAt < :cutoff
          and lower(f.email) not like concat('%@', :d1)
          and lower(f.email) not like concat('%@', :d2)
        order by f.lastEmailSentAt asc
    """)
    List<FarmLead> findFollowUpCandidatesExcludingDomains(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("d1") String d1,
            @Param("d2") String d2,
            Pageable pageable
    );
}