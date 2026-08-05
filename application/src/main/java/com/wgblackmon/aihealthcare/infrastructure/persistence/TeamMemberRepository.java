package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link TeamMemberEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, String> {

    List<TeamMemberEntity> findByTeamIdOrderByJoinedAtAsc(String teamId);

    Optional<TeamMemberEntity> findByTeamIdAndUserEmail(String teamId, String userEmail);

    Optional<TeamMemberEntity> findByUserEmail(String userEmail);
}
