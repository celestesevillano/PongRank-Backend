package org.example.pongrankbackend.Match.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Match.dto.LiveMatchAction;
import org.example.pongrankbackend.Match.dto.LiveMatchState;
import org.example.pongrankbackend.Match.service.LiveMatchService;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/matches/{matchId}/live")
@PreAuthorize("isAuthenticated()")
public class LiveMatchController {
    private final LiveMatchService live;

    public LiveMatchController(LiveMatchService live) {
        this.live = live;
    }

    @GetMapping
    public LiveMatchState get(@PathVariable Long matchId, @AuthenticationPrincipal CustomUserDetails user) {
        return live.get(matchId, user.getId());
    }

    @PostMapping
    public LiveMatchState apply(@PathVariable Long matchId, @AuthenticationPrincipal CustomUserDetails user,
                                @Valid @RequestBody LiveMatchAction action) {
        return live.apply(matchId, user.getId(), action);
    }
}
