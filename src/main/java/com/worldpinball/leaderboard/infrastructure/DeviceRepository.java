package com.worldpinball.leaderboard.infrastructure;

import com.worldpinball.leaderboard.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {}
