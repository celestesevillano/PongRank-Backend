package org.example.pongrankbackend.Tournament.repository;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.MatchType;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Tournament.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TournamentMatchRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TournamentMatchRepository tournamentMatchRepository;

    private Tournament tournament;
    private Player player1;
    private Player player2;
    private Match linkedMatch;
    private TournamentMatch groupMatch;
    private TournamentMatch knockoutMatch1;
    private TournamentMatch knockoutMatch2;

    @BeforeEach
    void setUp() {
        Player admin = Player.builder()
                .name("Admin Torneo")
                .email("admin.torneo@pongrank.com")
                .password("hash123")
                .role(Role.ROLE_CLUB_ADMIN)
                .status(PlayerStatus.ACTIVE)
                .build();
        admin = entityManager.persistAndFlush(admin);

        Club club = Club.builder()
                .name("Club Campeones")
                .address("Calle Deportes 100")
                .status(ClubStatus.APPROVED)
                .admin(admin)
                .affiliationDocumentUrl("https://docs.pongrank.com/campeones.pdf")
                .build();
        club = entityManager.persistAndFlush(club);

        tournament = Tournament.builder()
                .name("Gran Abierto de Lima")
                .club(club)
                .type(TournamentType.OPEN)
                .matchFormat(MatchFormat.BO5)
                .status(TournamentStatus.GROUP_STAGE)
                .startDate(LocalDateTime.now())
                .build();
        tournament = entityManager.persistAndFlush(tournament);

        player1 = Player.builder()
                .name("Hugo Calderano")
                .email("hugo@pongrank.com")
                .password("hash123")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .build();
        player1 = entityManager.persistAndFlush(player1);

        player2 = Player.builder()
                .name("Ma Long")
                .email("malong@pongrank.com")
                .password("hash123")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .build();
        player2 = entityManager.persistAndFlush(player2);

        linkedMatch = Match.builder()
                .player1(player1)
                .player2(player2)
                .format(MatchFormat.BO5)
                .matchType(MatchType.TOURNAMENT)
                .tournament(tournament)
                .status(MatchStatus.PROPOSED_P1)
                .build();
        linkedMatch = entityManager.persistAndFlush(linkedMatch);

        groupMatch = TournamentMatch.builder()
                .tournament(tournament)
                .stage(TournamentStage.GROUP)
                .groupNumber(1)
                .player1(player1)
                .player2(player2)
                .match(linkedMatch)
                .status(TournamentMatchStatus.SCHEDULED)
                .build();
        groupMatch = entityManager.persistAndFlush(groupMatch);

        knockoutMatch1 = TournamentMatch.builder()
                .tournament(tournament)
                .stage(TournamentStage.KNOCKOUT)
                .round(1)
                .bracketPosition(0)
                .player1(player1)
                .player2(player2)
                .status(TournamentMatchStatus.SCHEDULED)
                .build();
        knockoutMatch1 = entityManager.persistAndFlush(knockoutMatch1);

        knockoutMatch2 = TournamentMatch.builder()
                .tournament(tournament)
                .stage(TournamentStage.KNOCKOUT)
                .round(1)
                .bracketPosition(1)
                .status(TournamentMatchStatus.PENDING_PLAYERS)
                .build();
        knockoutMatch2 = entityManager.persistAndFlush(knockoutMatch2);
    }

    @Test
    @DisplayName("findByTournamentIdOrderByIdAsc - Retorna todos los partidos del torneo en orden ascendente de ID")
    void findByTournamentIdOrderByIdAsc_ReturnsAllMatchesInOrder() {
        List<TournamentMatch> matches = tournamentMatchRepository.findByTournamentIdOrderByIdAsc(tournament.getId());

        assertThat(matches).hasSize(3);
        assertThat(matches.get(0).getId()).isLessThan(matches.get(1).getId());
        assertThat(matches.get(1).getId()).isLessThan(matches.get(2).getId());
    }

    @Test
    @DisplayName("findByTournamentIdAndStage - Filtra correctamente partidos por etapa de torneo")
    void findByTournamentIdAndStage_ReturnsFilteredMatches() {
        List<TournamentMatch> groupMatches = tournamentMatchRepository.findByTournamentIdAndStage(
                tournament.getId(), TournamentStage.GROUP);
        List<TournamentMatch> knockoutMatches = tournamentMatchRepository.findByTournamentIdAndStage(
                tournament.getId(), TournamentStage.KNOCKOUT);

        assertThat(groupMatches).hasSize(1);
        assertThat(groupMatches.get(0).getId()).isEqualTo(groupMatch.getId());

        assertThat(knockoutMatches).hasSize(2);
        assertThat(knockoutMatches).extracting(TournamentMatch::getStage)
                .containsOnly(TournamentStage.KNOCKOUT);
    }

    @Test
    @DisplayName("findByTournamentIdAndStageAndRoundAndBracketPosition - Encuentra partido exacto de llave eliminatoria")
    void findByTournamentIdAndStageAndRoundAndBracketPosition_ReturnsExactMatch() {
        Optional<TournamentMatch> found = tournamentMatchRepository.findByTournamentIdAndStageAndRoundAndBracketPosition(
                tournament.getId(), TournamentStage.KNOCKOUT, 1, 0);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(knockoutMatch1.getId());
        assertThat(found.get().getBracketPosition()).isEqualTo(0);
        assertThat(found.get().getPlayer1().getName()).isEqualTo("Hugo Calderano");

        Optional<TournamentMatch> notFound = tournamentMatchRepository.findByTournamentIdAndStageAndRoundAndBracketPosition(
                tournament.getId(), TournamentStage.KNOCKOUT, 2, 0);
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("findByMatchId - Retorna el TournamentMatch asociado a una entidad Match")
    void findByMatchId_ReturnsLinkedTournamentMatch() {
        Optional<TournamentMatch> found = tournamentMatchRepository.findByMatchId(linkedMatch.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(groupMatch.getId());
        assertThat(found.get().getMatch().getId()).isEqualTo(linkedMatch.getId());
    }
}
