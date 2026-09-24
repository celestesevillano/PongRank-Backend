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
import org.example.pongrankbackend.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/communities")
@PreAuthorize("isAuthenticated()")
public class CommunityController {

    private final CommunityService communityService;
    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @PostMapping
    public ResponseEntity<CommunityResponseDTO> createCommunity(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CommunityCreateRequestDTO dto) {
        CommunityResponseDTO response = communityService.createCommunity(dto, currentUser.getId());

        URI location = UriComponentsBuilder.fromPath("/api/v1/communities/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<CommunityResponseDTO>> searchCommunities(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) CommunityType type,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(communityService.searchCommunities(name, type, pageable, currentUser.getId()));
    }

    @GetMapping("/me")
    public ResponseEntity<List<CommunityResponseDTO>> getMyCommunities(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(communityService.getMyCommunities(currentUser.getId()));
    }

    @GetMapping("/{communityId}")
    public ResponseEntity<CommunityDetailResponseDTO> getCommunityById(
            @PathVariable Long communityId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(communityService.getCommunityById(communityId, currentUser.getId()));
    }

    @PutMapping("/{communityId}")
    public ResponseEntity<CommunityResponseDTO> updateCommunity(
            @PathVariable Long communityId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CommunityUpdateRequestDTO dto) {
        return ResponseEntity.ok(communityService.updateCommunity(communityId, dto, currentUser.getId()));
    }

    @DeleteMapping("/{communityId}")
    public ResponseEntity<Void> archiveCommunity(
            @PathVariable Long communityId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        communityService.archiveCommunity(communityId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{communityId}/members")
    public ResponseEntity<Page<CommunityMemberResponseDTO>> getCommunityMembers(
            @PathVariable Long communityId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(communityService.getCommunityMembers(communityId, pageable, currentUser.getId()));
    }

    @GetMapping("/{communityId}/ranking")
    public ResponseEntity<Page<CommunityRankingEntryDTO>> getCommunityRanking(
            @PathVariable Long communityId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(communityService.getCommunityRanking(communityId, pageable, currentUser.getId()));
    }

    @PostMapping("/{communityId}/members")
    public ResponseEntity<CommunityMemberResponseDTO> addMember(
            @PathVariable Long communityId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestBody(required = false) CommunityMemberAddRequestDTO dto) {
        CommunityMemberResponseDTO response = communityService.addMember(communityId, dto, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{communityId}/members/{playerId}/role")
    public ResponseEntity<CommunityMemberResponseDTO> updateMemberRole(
            @PathVariable Long communityId,
            @PathVariable Long playerId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CommunityMemberRoleUpdateDTO dto) {
        return ResponseEntity.ok(
                communityService.updateMemberRole(communityId, playerId, dto, currentUser.getId()));
    }

    @DeleteMapping("/{communityId}/members/{playerId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long communityId,
            @PathVariable Long playerId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        communityService.removeMember(communityId, playerId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}