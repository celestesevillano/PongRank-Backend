package org.example.pongrankbackend.Federation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FederatedReviewRepositoryTest {
    @Autowired
    private FederatedReviewRepository reviews;

    @Test
    void storesPendingRequestAndVerifiesOnlyAfterDecision() {
        Long playerId = 41L;
        reviews.saveAndFlush(new FederatedReview(playerId, "Damas todo competidor"));

        assertThat(reviews.existsByPlayerIdAndStatus(playerId, FederatedReview.Status.VERIFIED)).isFalse();

        FederatedReview review = reviews.findForDecision(playerId).orElseThrow();
        assertThat(review.getStatus()).isEqualTo(FederatedReview.Status.PENDING);
        review.setStatus(FederatedReview.Status.VERIFIED);
        reviews.saveAndFlush(review);

        assertThat(reviews.existsByPlayerIdAndStatus(playerId, FederatedReview.Status.VERIFIED)).isTrue();
    }
}
