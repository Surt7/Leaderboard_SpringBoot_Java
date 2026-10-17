package com.worldpinball.leaderboard.application.features.iplicenses.getalliplicenses;

import java.util.UUID;

public record IpLicenseDto(
        UUID ipId,
        String name,
        String slug,
        PublisherDto publisher
) {}
