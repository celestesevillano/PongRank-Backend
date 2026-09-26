package org.example.pongrankbackend.Club.repository;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ClubRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ClubRepository clubRepository;

    private Player admin1;
    private Player admin2;
    private Club approvedClub;
    private Club pendingClub;

    @BeforeEach
    void setUp() {
        admin1 = Player.builder()
                .name("Admin Uno")
                .email("admin1@club.com")
                .password("hash123")
                .role(Role.ROLE_CLUB_ADMIN)
                .status(PlayerStatus.ACTIVE)
                .build();
        admin1 = entityManager.persistAndFlush(admin1);

        admin2 = Player.builder()
                .name("Admin Dos")
                .email("admin2@club.com")
                .password("hash123")
                .role(Role.ROLE_CLUB_ADMIN)
                .status(PlayerStatus.ACTIVE)
                .build();
        admin2 = entityManager.persistAndFlush(admin2);

        approvedClub = Club.builder()
                .name("Club Tenis de Mesa Miraflores")
                .address("Av. Larco 123, Miraflores")
                .status(ClubStatus.APPROVED)
                .admin(admin1)
                .affiliationDocumentUrl("https://docs.pongrank.com/miraflores.pdf")
                .build();
        approvedClub = entityManager.persistAndFlush(approvedClub);

        pendingClub = Club.builder()
                .name("Spin Club San Isidro")
                .address("Av. Javier Prado 456, San Isidro")
                .status(ClubStatus.PENDING)
                .admin(admin2)
                .affiliationDocumentUrl("https://docs.pongrank.com/sanisidro.pdf")
                .build();
        pendingClub = entityManager.persistAndFlush(pendingClub);
    }

    @Test
    @DisplayName("findByStatus - Retorna sólo los clubes con el estado solicitado paginados")
    void findByStatus_ReturnsClubsWithMatchingStatus() {
        Page<Club> approvedPage = clubRepository.findByStatus(ClubStatus.APPROVED, PageRequest.of(0, 10));

        assertThat(approvedPage.getTotalElements()).isEqualTo(1);
        assertThat(approvedPage.getContent().get(0).getName()).isEqualTo("Club Tenis de Mesa Miraflores");
        assertThat(approvedPage.getContent().get(0).getStatus()).isEqualTo(ClubStatus.APPROVED);

        Page<Club> pendingPage = clubRepository.findByStatus(ClubStatus.PENDING, PageRequest.of(0, 10));
        assertThat(pendingPage.getTotalElements()).isEqualTo(1);
        assertThat(pendingPage.getContent().get(0).getName()).isEqualTo("Spin Club San Isidro");
    }

    @Test
    @DisplayName("existsByNameIgnoreCase - Detecta nombres existentes sin importar mayúsculas o minúsculas")
    void existsByNameIgnoreCase_MatchesCaseInsensitively() {
        boolean existsUpper = clubRepository.existsByNameIgnoreCase("CLUB TENIS DE MESA MIRAFLORES");
        boolean existsLower = clubRepository.existsByNameIgnoreCase("club tenis de mesa miraflores");
        boolean existsDifferent = clubRepository.existsByNameIgnoreCase("Club Inexistente");

        assertThat(existsUpper).isTrue();
        assertThat(existsLower).isTrue();
        assertThat(existsDifferent).isFalse();
    }

    @Test
    @DisplayName("existsByAdminIdAndStatusIn - Verifica si un administrador ya tiene un club activo o en proceso")
    void existsByAdminIdAndStatusIn_ReturnsTrueWhenClubExistsForAdmin() {
        List<ClubStatus> activeStatuses = List.of(ClubStatus.PENDING, ClubStatus.APPROVED);

        boolean existsAdmin1 = clubRepository.existsByAdminIdAndStatusIn(admin1.getId(), activeStatuses);
        boolean existsAdmin2 = clubRepository.existsByAdminIdAndStatusIn(admin2.getId(), activeStatuses);

        assertThat(existsAdmin1).isTrue();
        assertThat(existsAdmin2).isTrue();

        boolean existsNonAdmin = clubRepository.existsByAdminIdAndStatusIn(9999L, activeStatuses);
        assertThat(existsNonAdmin).isFalse();
    }

    @Test
    @DisplayName("existsByAdminIdAndStatusInAndIdNot - Ignora el club especificado al validar unicidad de admin")
    void existsByAdminIdAndStatusInAndIdNot_IgnoresSelfClub() {
        List<ClubStatus> activeStatuses = List.of(ClubStatus.PENDING, ClubStatus.APPROVED);

        // admin1 administra approvedClub. Al consultar ignorando approvedClub.getId(), debe dar false
        boolean hasOther = clubRepository.existsByAdminIdAndStatusInAndIdNot(
                admin1.getId(), activeStatuses, approvedClub.getId());
        assertThat(hasOther).isFalse();

        // Si consultamos ignorando pendingClub.getId(), debe dar true porque todavía tiene approvedClub
        boolean hasApproved = clubRepository.existsByAdminIdAndStatusInAndIdNot(
                admin1.getId(), activeStatuses, pendingClub.getId());
        assertThat(hasApproved).isTrue();
    }
}
