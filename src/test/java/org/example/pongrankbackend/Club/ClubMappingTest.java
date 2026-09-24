package org.example.pongrankbackend.Club;

import org.example.pongrankbackend.Club.dto.ClubResponseDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewHistoryDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewResponseDTO;
import org.example.pongrankbackend.ClubMembership.ClubMembership;
import org.example.pongrankbackend.ClubMembership.ClubMembershipRole;
import org.example.pongrankbackend.ClubMembership.ClubMembershipStatus;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipResponseDTO;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.config.ModelMapperConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the team's real ModelMapper configuration to detect mapping errors that mocks cannot catch
class ClubMappingTest {

    private final ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    private final Player admin = Player.builder().id(1L).name("Ana Admin").email("ana@test.com").password("secret").build();

    private final Club club = Club.builder()
            .id(10L)
            .name("Club Lima")
            .address("Av. Lima 123")
            .affiliationDocumentUrl("https://docs.test/afiliacion.pdf")
            .rejectionReason("Documento ilegible")
            .admin(admin)
            .status(ClubStatus.REJECTED)
            .build();

    @Test
    @DisplayName("Club -> ClubResponseDTO: expone datos públicos y el admin como PlayerSummaryDTO")
    void mapsClubToPublicResponse() {
        ClubResponseDTO dto = modelMapper.map(club, ClubResponseDTO.class);

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getName()).isEqualTo("Club Lima");
        assertThat(dto.getStatus()).isEqualTo(ClubStatus.REJECTED);
        assertThat(dto.getAdmin().getId()).isEqualTo(1L);
        assertThat(dto.getAdmin().getName()).isEqualTo("Ana Admin");
    }

    @Test
    @DisplayName("Club -> ClubReviewResponseDTO: incluye documento y motivo de rechazo")
    void mapsClubToReviewResponse() {
        ClubReviewResponseDTO dto = modelMapper.map(club, ClubReviewResponseDTO.class);

        assertThat(dto.getAffiliationDocumentUrl()).isEqualTo("https://docs.test/afiliacion.pdf");
        assertThat(dto.getRejectionReason()).isEqualTo("Documento ilegible");
        assertThat(dto.getAdmin().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("ClubMembership -> ClubMembershipResponseDTO: mapea clubId, clubName, jugador y rol")
    void mapsMembershipToResponse() {
        Player member = Player.builder().id(3L).name("Beto Miembro").build();
        ClubMembership membership = ClubMembership.builder()
                .id(50L)
                .player(member)
                .club(club)
                .status(ClubMembershipStatus.APPROVED)
                .role(ClubMembershipRole.MEMBER)
                .build();

        ClubMembershipResponseDTO dto = modelMapper.map(membership, ClubMembershipResponseDTO.class);

        assertThat(dto.getId()).isEqualTo(50L);
        assertThat(dto.getClubId()).isEqualTo(10L);
        assertThat(dto.getClubName()).isEqualTo("Club Lima");
        assertThat(dto.getPlayer().getId()).isEqualTo(3L);
        assertThat(dto.getPlayer().getName()).isEqualTo("Beto Miembro");
        assertThat(dto.getStatus()).isEqualTo(ClubMembershipStatus.APPROVED);
        assertThat(dto.getRole()).isEqualTo(ClubMembershipRole.MEMBER);
    }

    @Test
    @DisplayName("ClubReview -> ClubReviewHistoryDTO: mapea resultado, motivo y revisor")
    void mapsReviewToHistory() {
        Player reviewer = Player.builder().id(99L).name("Admin General").build();
        ClubReview review = ClubReview.builder()
                .id(7L)
                .club(club)
                .reviewer(reviewer)
                .result(ClubStatus.REJECTED)
                .reason("Documento ilegible")
                .affiliationDocumentUrl("https://docs.test/afiliacion.pdf")
                .build();

        ClubReviewHistoryDTO dto = modelMapper.map(review, ClubReviewHistoryDTO.class);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getResult()).isEqualTo(ClubStatus.REJECTED);
        assertThat(dto.getReason()).isEqualTo("Documento ilegible");
        assertThat(dto.getReviewer().getName()).isEqualTo("Admin General");
    }
}
