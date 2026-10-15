package com.worldpinball.leaderboard.api;

import com.worldpinball.leaderboard.application.LeaderboardService;
import com.worldpinball.leaderboard.application.dto.LeaderboardEntryDto;
import com.worldpinball.leaderboard.application.dto.SubmitScoreRequest;
import com.worldpinball.leaderboard.domain.ScoreEntry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leaderboards")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping("/{ipId}/{modeId}")
    public ResponseEntity<List<LeaderboardEntryDto>> getTopScores(
        @PathVariable UUID ipId,
        @PathVariable UUID modeId,
        @RequestParam(defaultValue = "10") int limit
    ) {
        List<LeaderboardEntryDto> leaderboard = leaderboardService.getTopScores(ipId, modeId, limit);
        return ResponseEntity.ok(leaderboard);
    }

    @PostMapping("/scores")
    public ResponseEntity<ScoreEntry> submitScore(@Valid @RequestBody SubmitScoreRequest request) {
        ScoreEntry created = leaderboardService.submitScore(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{ipId}/{modeId}/rank")
    public ResponseEntity<Map<String, Object>> getRank(
        @PathVariable UUID ipId,
        @PathVariable UUID modeId,
        @RequestParam long score
    ) {
        long rank = leaderboardService.getRank(ipId, modeId, score);
        return ResponseEntity.ok(Map.of(
            "ipId", ipId,
            "modeId", modeId,
            "score", score,
            "rank", rank
        ));
    }
}
