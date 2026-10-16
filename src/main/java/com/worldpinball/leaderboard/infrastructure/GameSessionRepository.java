package com.worldpinball.leaderboard.infrastructure;

import com.worldpinball.leaderboard.domain.entities.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {}
