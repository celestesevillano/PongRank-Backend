package org.example.pongrankbackend.Federation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.example.pongrankbackend.security.SecurityUtils;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FederatedReviewController {
    private final FederatedReviewRepository reviews;
    private final FederatedRankingRepository ranking;
    private final FederatedReviewService service;

    record Decision(@NotNull Long rankingEntryId) {}
    record Rejection(@NotBlank @Size(max = 500) String reason) {}
    record Review(Long playerId, String category, FederatedReview.Status status, Instant submittedAt,
                  Instant reviewedAt, String reason) {
        static Review from(FederatedReview review) {
            return new Review(review.getPlayerId(), review.getCategory(), review.getStatus(), review.getSubmittedAt(),
                    review.getReviewedAt(), review.getReason());
        }
    }
    record AdminReview(Review review, String playerName, Long reviewedBy, Long rankingEntryId) {}
    record RankingEntry(Long id, String name, String club, String category, LocalDate rankingDate, int rankingPosition) {
        static RankingEntry from(FederatedRankingEntry entry) {
            return new RankingEntry(entry.getId(), entry.getName(), entry.getClub(), entry.getCategory(),
                    entry.getRankingDate(), entry.getRankingPosition());
        }
    }

    @GetMapping("/players/me/fdptm-review")
    public ResponseEntity<Review> mine() {
        return ResponseEntity.of(reviews.findById(SecurityUtils.getRequiredCurrentUserId()).map(Review::from));
    }

    record Verification(boolean verified) {}

    @GetMapping("/players/{playerId}/fdptm-verification")
    public Verification verification(@PathVariable Long playerId) {
        return new Verification(service.isVerified(playerId));
    }

    @GetMapping("/admin/fdptm/reviews")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public PageResponseDTO<AdminReview> list(
            @RequestParam(defaultValue = "PENDING") FederatedReview.Status status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponseDTO.from(reviews.findByStatus(status,
                PageRequestFactory.of(page, size, Sort.by("submittedAt", "playerId"))), this::adminReview);
    }

    @GetMapping("/admin/fdptm/ranking")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public PageResponseDTO<RankingEntry> ranking(@RequestParam String category, @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequestFactory.of(page, size, Sort.by(Sort.Order.desc("rankingDate"), Sort.Order.asc("rankingPosition")));
        return PageResponseDTO.from(ranking.findByCategoryIgnoreCaseAndNameContainingIgnoreCase(category.strip(),
                name.strip(), pageable), RankingEntry::from);
    }

    @PatchMapping("/admin/fdptm/reviews/{playerId}/verify")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public AdminReview verify(@PathVariable Long playerId, @Valid @RequestBody Decision decision) {
        return adminReview(service.decide(playerId, FederatedReview.Status.VERIFIED, decision.rankingEntryId(), null));
    }

    @PatchMapping("/admin/fdptm/reviews/{playerId}/reject")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public AdminReview reject(@PathVariable Long playerId, @Valid @RequestBody Rejection rejection) {
        return adminReview(service.decide(playerId, FederatedReview.Status.REJECTED, null, rejection.reason().strip()));
    }

    private AdminReview adminReview(FederatedReview review) {
        return new AdminReview(Review.from(review), service.playerName(review.getPlayerId()), review.getReviewedBy(),
                review.getRankingEntryId());
    }
}
