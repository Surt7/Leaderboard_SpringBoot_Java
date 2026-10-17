package com.worldpinball.leaderboard.application.features.iplicenses.getalliplicenses;

import java.util.UUID;

public record PublisherDto(UUID publisherId, String name, String slug) {}
