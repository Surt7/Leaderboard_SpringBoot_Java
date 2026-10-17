package com.worldpinball.leaderboard.application.features.machines.Admin.getallmachines;

import com.worldpinball.leaderboard.domain.entities.IpLicense;
import com.worldpinball.leaderboard.domain.entities.Machine;
import com.worldpinball.leaderboard.infrastructure.IpLicenseRepository;
import com.worldpinball.leaderboard.infrastructure.MachineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetAllMachinesHandlerImpl implements GetAllMachinesHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetAllMachinesHandlerImpl.class);

    private final MachineRepository machineRepository;
    private final IpLicenseRepository ipLicenseRepository;

    public GetAllMachinesHandlerImpl(MachineRepository machineRepository, IpLicenseRepository ipLicenseRepository) {
        this.machineRepository = machineRepository;
        this.ipLicenseRepository = ipLicenseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public GetAllMachinesResult handle(GetAllMachinesQuery query) {
        logger.info("[3100:FetchingMachines] Fetching all registered machines.");
        List<Machine> machines = machineRepository.findAll();

        if (machines.isEmpty()) {
            logger.warn("[3101:NoMachinesFound] No machines found in the system.");
            return new GetAllMachinesResult.NotFound();
        }

        Set<UUID> ipIds = machines.stream()
                .map(Machine::getIpId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> ipNameMap = ipLicenseRepository.findAllById(ipIds).stream()
                .collect(Collectors.toMap(IpLicense::getIpId, IpLicense::getName));

        List<MachineRowDto> dtos = machines.stream()
                .map(m -> new MachineRowDto(
                        m.getMachineId(),
                        ipNameMap.getOrDefault(m.getIpId(), "Unknown IP"),
                        m.getDisplayName(),
                        m.getStatus(),
                        m.getMachineNotes(),
                        m.getRegisteredAtUtc(),
                        m.getUpdatedAtUtc(),
                        m.getWorldPinballSerialNumber()
                ))
                .toList();

        return new GetAllMachinesResult.Success(dtos);
    }
}
