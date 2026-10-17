package com.worldpinball.leaderboard.application.features.iplicenses.getalliplicenses;

import java.util.List;

public sealed interface GetAllIpLicensesResult {
    record Success(List<IpLicenseDto> ipLicenses) implements GetAllIpLicensesResult {}
    record NotFound() implements GetAllIpLicensesResult {}
}
