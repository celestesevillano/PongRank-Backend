package org.example.pongrankbackend.Match.repository;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.MatchType;
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
class MatchRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MatchRepository matchRepository;

    private Player player1;
    private Player player2;
    private Player player3;
    private Match match1;
    private Match match2;

    @BeforeEach
    void setUp() {
        player1 = entityManager.persistAndFlush(Player.builder()
                .name("Player One")
                .email("p1@pongrank.com")
                .password("hash123")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .build());

        player2 = entityManager.persistAndFlush(Player.builder()
                .name("Player Two")
                .email("p2@pongrank.com")
                .password("hash123")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .build());

        player3 = entityManager.persistAndFlush(Player.builder()
                .name("Player Three")
                .email("p3@pongrank.com")
                .password("hash123")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .build());

        match1 = entityManager.persistAndFlush(Match.builder()
                .player1(player1)
                .player2(player2)
                .format(MatchFormat.BO3)
                .matchType(MatchType.FRIEND)
                .status(MatchStatus.CONFIRMED)
                .build());

        match2 = entityManager.persistAndFlush(Match.builder()
                .player1(player2)
                .player2(player3)
                .format(MatchFormat.BO5)
                .matchType(MatchType.LOCATION)
                .status(MatchStatus.CREATED)
                .build());
    }

    @Test
    @DisplayName("findAllByPlayerId - Retorna partidos donde el jugador es player1 o player2")
    void findAllByPlayerId_ReturnsMatchesForPlayer() {
        List<Match> matches = matchRepository.findAllByPlayerId(player1.getId());

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getId()).isEqualTo(match1.getId());

        List<Match> matchesP2 = matchRepository.findAllByPlayerId(player2.getId());
        assertThat(matchesP2).hasSize(2);
    }

    @Test
    @DisplayName("findAllByPlayerIdAndStatus - Filtra correctamente por jugador y estado")
    void findAllByPlayerIdAndStatus_FiltersByStatus() {
        List<Match> confirmed = matchRepository.findAllByPlayerIdAndStatus(player2.getId(), MatchStatus.CONFIRMED);
        assertThat(confirmed).hasSize(1);
        assertThat(confirmed.get(0).getId()).isEqualTo(match1.getId());

        List<Match> created = matchRepository.findAllByPlayerIdAndStatus(player2.getId(), MatchStatus.CREATED);
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getId()).isEqualTo(match2.getId());
    }

    @Test
    @DisplayName("findAllByPlayerId con Pageable - Retorna página de partidos")
    void findAllByPlayerId_WithPagination_ReturnsPage() {
        Page<Match> page = matchRepository.findAllByPlayerId(player2.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("findOpenChallenges - Encuentra partidos abiertos sin player2")
    void findOpenChallenges_ReturnsOnlyOpenChallenges() {
        Match openChallenge = entityManager.persistAndFlush(Match.builder()
                .player1(player1)
                .player2(null)
                .format(MatchFormat.BO3)
                .matchType(MatchType.LOCATION)
                .status(MatchStatus.CREATED)
                .build());

        List<Match> openMatches = matchRepository.findOpenChallenges(MatchStatus.CREATED, MatchType.LOCATION);

        assertThat(openMatches).hasSize(1);
        assertThat(openMatches.get(0).getId()).isEqualTo(openChallenge.getId());
        assertThat(openMatches.get(0).getPlayer2()).isNull();
    }
}
