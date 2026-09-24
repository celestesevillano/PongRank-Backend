package org.example.pongrankbackend.Community.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Community.CommunityType;
import org.example.pongrankbackend.Community.dto.CommunityCreateRequestDTO;
import org.example.pongrankbackend.Community.dto.CommunityDetailResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityUpdateRequestDTO;
import org.example.pongrankbackend.Community.service.CommunityService;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberAddRequestDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberResponseDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberRoleUpdateDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityRankingEntryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;


@RestController
@RequestMapping("/api/v1/communities")
public class CommunityController {

    private final CommunityService communityService;
    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @PostMapping
    public ResponseEntity<CommunityResponseDTO> createCommunity(
            @Valid @RequestBody CommunityCreateRequestDTO dto,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext when JWT is integrated

        CommunityResponseDTO response = communityService.createCommunity(dto, requesterId);

        URI location = UriComponentsBuilder.fromPath("/api/v1/communities/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<CommunityResponseDTO>> searchCommunities(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) CommunityType type,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.searchCommunities(name, type, pageable, requesterId));
    }


    @GetMapping("/me")
    public ResponseEntity<List<CommunityResponseDTO>> getMyCommunities(
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.getMyCommunities(requesterId));
    }


    @GetMapping("/{communityId}")
    public ResponseEntity<CommunityDetailResponseDTO> getCommunityById(
            @PathVariable Long communityId,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.getCommunityById(communityId, requesterId));
    }


    @PutMapping("/{communityId}")
    public ResponseEntity<CommunityResponseDTO> updateCommunity(
            @PathVariable Long communityId,
            @Valid @RequestBody CommunityUpdateRequestDTO dto,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.updateCommunity(communityId, dto, requesterId));
    }


    @DeleteMapping("/{communityId}")
    public ResponseEntity<Void> archiveCommunity(
            @PathVariable Long communityId,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        communityService.archiveCommunity(communityId, requesterId);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/{communityId}/members")
    public ResponseEntity<Page<CommunityMemberResponseDTO>> getCommunityMembers(
            @PathVariable Long communityId,
            @PageableDefault(size = 20) Pageable pageable,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.getCommunityMembers(communityId, pageable, requesterId));
    }


    @GetMapping("/{communityId}/ranking")
    public ResponseEntity<Page<CommunityRankingEntryDTO>> getCommunityRanking(
            @PathVariable Long communityId,
            @PageableDefault(size = 20) Pageable pageable,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.getCommunityRanking(communityId, pageable, requesterId));
    }


    @PostMapping("/{communityId}/members")
    public ResponseEntity<CommunityMemberResponseDTO> addMember(
            @PathVariable Long communityId,
            @RequestBody(required = false) CommunityMemberAddRequestDTO dto,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        CommunityMemberResponseDTO response = communityService.addMember(communityId, dto, requesterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @PatchMapping("/{communityId}/members/{playerId}/role")
    public ResponseEntity<CommunityMemberResponseDTO> updateMemberRole(
            @PathVariable Long communityId,
            @PathVariable Long playerId,
            @Valid @RequestBody CommunityMemberRoleUpdateDTO dto,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(
                communityService.updateMemberRole(communityId, playerId, dto, requesterId));
    }


    @DeleteMapping("/{communityId}/members/{playerId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long communityId,
            @PathVariable Long playerId,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        communityService.removeMember(communityId, playerId, requesterId);
        return ResponseEntity.noContent().build();
    }
}