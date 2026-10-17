package com.worldpinball.leaderboard.api.controllers.Admin;

import com.worldpinball.leaderboard.application.features.leaderboard.Admin.dashboard.GetMachineDashboardHandler;
import com.worldpinball.leaderboard.application.features.leaderboard.Admin.dashboard.GetMachineDashboardQuery;
import com.worldpinball.leaderboard.application.features.leaderboard.Admin.dashboard.GetMachineDashboardResult;
import com.worldpinball.leaderboard.application.features.leaderboard.Admin.dashboard.MachineDashboardDto;
import com.worldpinball.leaderboard.application.features.machines.Admin.getallmachines.GetAllMachinesHandler;
import com.worldpinball.leaderboard.application.features.machines.Admin.getallmachines.GetAllMachinesQuery;
import com.worldpinball.leaderboard.application.features.machines.Admin.getallmachines.GetAllMachinesResult;
import com.worldpinball.leaderboard.application.features.machines.Admin.getallmachines.MachineRowDto;
import com.worldpinball.leaderboard.application.features.machines.Admin.provision.ProvisionMachineHandler;
import com.worldpinball.leaderboard.application.features.machines.Admin.provision.ProvisionMachineResult;
import com.worldpinball.leaderboard.application.features.machines.Admin.provision.commands.ProvisionMachineCommand;
import com.worldpinball.leaderboard.application.features.machines.Admin.provision.dtos.ProvisionMachineRequest;
import com.worldpinball.leaderboard.application.features.machines.Admin.provision.dtos.ProvisionMachineResponse;
import com.worldpinball.leaderboard.application.features.machines.Admin.reissuetoken.ReissueEnrollmentTokenHandler;
import com.worldpinball.leaderboard.application.features.machines.Admin.reissuetoken.ReissueEnrollmentTokenResult;
import com.worldpinball.leaderboard.application.features.machines.Admin.reissuetoken.commands.ReissueEnrollmentTokenCommand;
import com.worldpinball.leaderboard.application.features.machines.Admin.reissuetoken.dtos.ReissueEnrollmentTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/machines")
@Tag(name = "Admin Machines", description = "Administration of machines and provisioning")
public class AdminMachinesController {

    private final GetAllMachinesHandler getAllMachinesHandler;
    private final ProvisionMachineHandler provisionMachineHandler;
    private final ReissueEnrollmentTokenHandler reissueEnrollmentTokenHandler;
    private final GetMachineDashboardHandler getMachineDashboardHandler;

    public AdminMachinesController(
            GetAllMachinesHandler getAllMachinesHandler,
            ProvisionMachineHandler provisionMachineHandler,
            ReissueEnrollmentTokenHandler reissueEnrollmentTokenHandler,
            GetMachineDashboardHandler getMachineDashboardHandler
    ) {
        this.getAllMachinesHandler = getAllMachinesHandler;
        this.provisionMachineHandler = provisionMachineHandler;
        this.reissueEnrollmentTokenHandler = reissueEnrollmentTokenHandler;
        this.getMachineDashboardHandler = getMachineDashboardHandler;
    }

    @GetMapping
    @Operation(summary = "Retrieves all registered machines")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of machines",
                    content = @Content(schema = @Schema(implementation = MachineRowDto.class))),
            @ApiResponse(responseCode = "404", description = "No machines found")
    })
    public ResponseEntity<?> getAllMachines() {
        GetAllMachinesResult result = getAllMachinesHandler.handle(new GetAllMachinesQuery());

        return switch (result) {
            case GetAllMachinesResult.Success s -> ResponseEntity.ok(s.machines());
            case GetAllMachinesResult.NotFound ignored -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No machines found."));
        };
    }

    @PostMapping("/provision")
    @Operation(summary = "Creates a new machine and generates an enrollment token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Machine successfully provisioned",
                    content = @Content(schema = @Schema(implementation = ProvisionMachineResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Unknown or inactive IP"),
            @ApiResponse(responseCode = "409", description = "Idempotency key or serial number conflict")
    })
    public ResponseEntity<?> provision(
            @Valid @RequestBody ProvisionMachineRequest req,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyHeader,
            Principal principal
    ) {
        String issuedByUser = principal != null && principal.getName() != null ? principal.getName() : "admin";

        UUID idempotencyKey;
        try {
            idempotencyKey = (idempotencyHeader != null && !idempotencyHeader.isBlank())
                    ? UUID.fromString(idempotencyHeader.trim())
                    : UUID.randomUUID();
        } catch (IllegalArgumentException e) {
            idempotencyKey = UUID.randomUUID();
        }

        ProvisionMachineCommand cmd = new ProvisionMachineCommand(
                req.worldPinballSerialNumber(),
                req.ipSlug(),
                req.displayName(),
                idempotencyKey,
                issuedByUser
        );

        ProvisionMachineResult result = provisionMachineHandler.handle(cmd);

        return switch (result) {
            case ProvisionMachineResult.Success success -> ResponseEntity.ok(
                    new ProvisionMachineResponse(success.machineId(), success.enrollmentToken())
            );
            case ProvisionMachineResult.UnknownIp unknownIp -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Unknown or inactive Ip slug: " + unknownIp.ipSlug()));
            case ProvisionMachineResult.SameIdempotencyKey key -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "A machine was already provisioned with idempotency key: " + key.idempotencyKey()));
            case ProvisionMachineResult.DuplicateSerial dup -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Already existing serial number: " + dup.serialNumber()));
        };
    }

    @PostMapping("/{machineId}/tokens")
    @Operation(summary = "Reissues a new enrollment token for an existing machine")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token reissued successfully",
                    content = @Content(schema = @Schema(implementation = ReissueEnrollmentTokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid machine status"),
            @ApiResponse(responseCode = "404", description = "Machine not found")
    })
    public ResponseEntity<?> reissueToken(
            @PathVariable UUID machineId,
            Principal principal
    ) {
        String issuedByUser = principal != null && principal.getName() != null ? principal.getName() : "admin";

        ReissueEnrollmentTokenCommand cmd = new ReissueEnrollmentTokenCommand(machineId, issuedByUser);
        ReissueEnrollmentTokenResult result = reissueEnrollmentTokenHandler.handle(cmd);

        return switch (result) {
            case ReissueEnrollmentTokenResult.Success success -> ResponseEntity.ok(
                    new ReissueEnrollmentTokenResponse(success.machineId(), success.enrollmentToken(), success.expiresAtUtc())
            );
            case ReissueEnrollmentTokenResult.MachineNotFound notFound -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Machine with ID '" + notFound.machineId() + "' could not be found."));
            case ReissueEnrollmentTokenResult.InvalidMachineStatus invalid -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Cannot reissue token: Machine is in status '" + invalid.status() + "' (expected PendingEnrollment)."));
        };
    }

    @GetMapping("/{machineId}/dashboard")
    @Operation(summary = "Retrieves aggregate statistics for a machine dashboard")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard stats retrieved successfully",
                    content = @Content(schema = @Schema(implementation = MachineDashboardDto.class))),
            @ApiResponse(responseCode = "404", description = "Machine statistics not found")
    })
    public ResponseEntity<?> getDashboard(@PathVariable UUID machineId) {
        GetMachineDashboardResult result = getMachineDashboardHandler.handle(new GetMachineDashboardQuery(machineId));

        return switch (result) {
            case GetMachineDashboardResult.Success success -> ResponseEntity.ok(success.stats());
            case GetMachineDashboardResult.NotFound notFound -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Machine '" + notFound.machineId() + "' not found."));
        };
    }
}
