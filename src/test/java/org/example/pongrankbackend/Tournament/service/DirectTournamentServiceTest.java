package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentStatus;
import org.example.pongrankbackend.Tournament.dto.DirectTournamentRequestDTO;
import org.example.pongrankbackend.Tournament.repository.TournamentMatchRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentParticipantRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class DirectTournamentServiceTest {
    private final ClubRepository clubs = mock(ClubRepository.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final TournamentRepository tournaments = mock(TournamentRepository.class);
    private final TournamentParticipantRepository participants = mock(TournamentParticipantRepository.class);
    private final TournamentMatchRepository matches = mock(TournamentMatchRepository.class);
    private final TournamentAccessPolicy access = mock(TournamentAccessPolicy.class);
    private final TournamentMatchScheduler scheduler = mock(TournamentMatchScheduler.class);
    private final DirectTournamentService service = new DirectTournamentService(
            clubs, players, tournaments, participants, matches, access, mock(TournamentMapper.class), scheduler);
    private final Club club = Club.builder().id(10L).status(ClubStatus.APPROVED).build();
    private final DirectTournamentRequestDTO request = new DirectTournamentRequestDTO(
            10L, " Copa ", MatchFormat.BO3, Instant.parse("2026-12-01T20:00:00Z"), "same-request",
            List.of(1L, 2L, 3L, 4L));

    @Test
    void publishesParticipantsAndSchedulesOnlyTheReadyFixtures() {
        when(clubs.findByIdForUpdate(10L)).thenReturn(Optional.of(club));
        when(players.findAllById(Set.of(1L, 2L, 3L, 4L))).thenReturn(new ArrayList<>(List.of(
                Player.builder().id(4L).ratingGlicko(1700.0).build(), Player.builder().id(2L).build(),
                Player.builder().id(3L).build(), Player.builder().id(1L).ratingGlicko(1800.0).build())));
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getRequiredCurrentUserId).thenReturn(1L);
            service.publish(request);
        }
        verify(access).validateClubAdmin(club, 1L);
        verify(tournaments).save(argThat(t -> t.isDirectElimination()
                && t.getStatus() == TournamentStatus.KNOCKOUT_STAGE && t.getName().equals("Copa")));
        verify(participants).saveAll(argThat(entries ->
                java.util.stream.StreamSupport.stream(entries.spliterator(), false)
                        .map(entry -> entry.getPlayer().getId()).toList().equals(List.of(1L, 4L, 2L, 3L))));
        verify(matches).saveAll(argThat(fixtures -> fixtures.spliterator().getExactSizeIfKnown() == 3));
        verify(scheduler, times(2)).schedule(any(), argThat(fixture -> fixture.getRound() == 1));
    }

    @Test
    void retryReturnsTheAlreadyPublishedTournamentWithoutCreatingFixturesAgain() {
        when(clubs.findByIdForUpdate(10L)).thenReturn(Optional.of(club));
        when(tournaments.findByClubIdAndPublicationKey(10L, "same-request")).thenReturn(Optional.of(Tournament.builder().id(21L).build()));
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getRequiredCurrentUserId).thenReturn(1L);
            service.publish(request);
        }
        verify(access).validateClubAdmin(club, 1L);
        verify(tournaments, never()).save(any());
        verifyNoInteractions(players, participants, matches, scheduler);
    }

    @Test
    void neverPersistsATournamentWithAnIneligiblePlayer() {
        when(clubs.findByIdForUpdate(10L)).thenReturn(Optional.of(club));
        Player ineligible = Player.builder().id(4L).build();
        when(players.findAllById(Set.of(1L, 2L, 3L, 4L))).thenReturn(new ArrayList<>(List.of(
                ineligible, Player.builder().id(2L).build(), Player.builder().id(3L).build(), Player.builder().id(1L).build())));
        when(access.eligibilityError(any(), eq(ineligible))).thenReturn("El jugador ya no pertenece al club");
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getRequiredCurrentUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.publish(request)).isInstanceOf(ConflictException.class);
        }
        verify(tournaments, never()).save(any());
        verifyNoInteractions(participants, matches, scheduler);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 8, 12, 16, 20, 32})
    void buildsOneConnectedKnockoutWithTopSeedsOnOppositeSides(int count) {
        List<Player> seeds = LongStream.rangeClosed(1, count)
                .mapToObj(id -> Player.builder().id(id).build()).toList();
        List<TournamentMatch> fixtures = DirectTournamentService.fixtures(Tournament.builder().build(), seeds);
        int principal = Integer.highestOneBit(count);
        int preliminaries = count - principal;
        int mainRound = preliminaries == 0 ? 1 : 2;
        int directCount = principal - preliminaries;
        int finalRound = mainRound + Integer.numberOfTrailingZeros(principal) - 1;

        assertThat(fixtures).hasSize(count - 1);
        assertThat(fixtures.stream().filter(match -> match.getRound() == finalRound)).hasSize(1);
        assertThat(fixtures.stream().filter(match -> match.getRound() == mainRound)).hasSize(principal / 2);
        assertThat(fixtures.stream().filter(match -> match.getRound() < mainRound)).hasSize(preliminaries);
        assertThat(fixtures.stream().filter(match -> match.getRound() < mainRound)
                .flatMap(match -> Stream.of(match.getPlayer1(), match.getPlayer2()))
                .map(Player::getId)).containsExactlyInAnyOrderElementsOf(
                        LongStream.rangeClosed(directCount + 1, count).boxed().toList());
        assertThat(fixtures.stream().filter(match -> match.getRound() == mainRound)
                .flatMap(match -> Stream.of(match.getPlayer1(), match.getPlayer2()))
                .filter(java.util.Objects::nonNull).map(Player::getId))
                .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, directCount).boxed().toList());

        var positions = fixtures.stream().collect(Collectors.toMap(
                match -> match.getRound() + ":" + match.getBracketPosition(), Function.identity()));
        for (TournamentMatch match : fixtures) {
            if (match.getRound() < finalRound) {
                TournamentMatch next = positions.get((match.getRound() + 1) + ":" + match.getBracketPosition() / 2);
                assertThat(next).isNotNull();
                assertThat(match.getBracketPosition() % 2 == 0 ? next.getPlayer1() : next.getPlayer2()).isNull();
            }
        }
        TournamentMatch firstSeed = fixtures.stream().filter(match -> match.hasPlayer(1L)).findFirst().orElseThrow();
        TournamentMatch secondSeed = fixtures.stream().filter(match -> match.hasPlayer(2L)).findFirst().orElseThrow();
        assertThat(firstSeed.getBracketPosition()).isLessThan(principal / 4);
        assertThat(secondSeed.getBracketPosition()).isGreaterThanOrEqualTo(principal / 4);
    }
}
