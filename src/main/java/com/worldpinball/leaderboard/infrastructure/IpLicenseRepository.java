package com.worldpinball.leaderboard.infrastructure;

import com.worldpinball.leaderboard.domain.IpLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IpLicenseRepository extends JpaRepository<IpLicense, UUID> {}
