package org.example.pongrankbackend.Federation;

import lombok.RequiredArgsConstructor;
import org.example.pongrankbackend.Player.Gender;
import org.example.pongrankbackend.Player.InitialLevel;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.Player.service.AgeCategorySuggester;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.pongrankbackend.common.exception.ApiException;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FederatedReviewService {
    private final FederatedReviewRepository reviews;
    private final FederatedRankingRepository ranking;
    private final PlayerRepository players;
    private final AgeCategorySuggester categories;

    public void validateRegistration(PlayerRegisterRequestDTO request) {
        if (!Boolean.TRUE.equals(request.getFederatedDeclared())) return;
        if (request.getInitialLevel() != InitialLevel.ADVANCED && request.getInitialLevel() != InitialLevel.EXPERT) {
            throw invalid("La declaración FDPTM requiere nivel Avanzado o Experto");
        }
        if (request.getBirthDate() == null || request.getGender() == null || request.getGender() == Gender.PREFER_NOT_TO_SAY) {
            throw invalid("Se necesitan la fecha de nacimiento y el sexo para comprobar la categoría FDPTM");
        }
        String expected = categories.suggest(request.getBirthDate(), request.getGender(), LocalDate.now());
        if (request.getCategoryFdptm() == null || !expected.equalsIgnoreCase(request.getCategoryFdptm().strip())) {
            throw invalid("La categoría FDPTM no coincide con los datos del registro");
        }
        request.setCategoryFdptm(expected);
    }

    @Transactional
    public void submit(Player player) {
        reviews.save(new FederatedReview(player.getId(), player.getCategoryFdptm()));
    }

    @Transactional
    public FederatedReview decide(Long playerId, FederatedReview.Status decision, Long entryId, String reason) {
        FederatedReview review = reviews.findForDecision(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud FDPTM no encontrada"));
        if (review.getStatus() != FederatedReview.Status.PENDING) {
            throw new ConflictException("Esta solicitud ya fue revisada");
        }
        if (playerId.equals(SecurityUtils.getRequiredCurrentUserId())) {
            throw invalid("No puedes revisar tu propia solicitud FDPTM");
        }
        if (decision == FederatedReview.Status.VERIFIED) {
            FederatedRankingEntry entry = ranking.findById(entryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Registro del padrón no encontrado"));
            if (!review.getCategory().equalsIgnoreCase(entry.getCategory())) {
                throw invalid("El registro del padrón pertenece a otra categoría");
            }
            review.setRankingEntryId(entry.getId());
        }
        review.setStatus(decision);
        review.setReason(reason);
        review.setReviewedBy(SecurityUtils.getRequiredCurrentUserId());
        review.setReviewedAt(Instant.now());
        return reviews.saveAndFlush(review);
    }

    public boolean isVerified(Long playerId) {
        return reviews.existsByPlayerIdAndStatus(playerId, FederatedReview.Status.VERIFIED);
    }

    public String playerName(Long playerId) {
        return players.findById(playerId).map(Player::getName).orElse(null);
    }

    private ApiException invalid(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST) {};
    }
}
