package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.ClubMembership.service.ClubMembershipService;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentMatchStatus;
import org.example.pongrankbackend.Tournament.TournamentParticipant;
import org.example.pongrankbackend.Tournament.TournamentStage;
import org.example.pongrankbackend.Tournament.TournamentStatus;
import org.example.pongrankbackend.Tournament.TournamentType;
import org.example.pongrankbackend.Tournament.dto.TournamentCreateRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentWalkoverRequestDTO;
import org.example.pongrankbackend.Tournament.engine.GroupDistributor;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder;
import org.example.pongrankbackend.Tournament.engine.RoundRobinScheduler;
import org.example.pongrankbackend.Tournament.integration.MatchIntegrationPort;
import org.example.pongrankbackend.Tournament.integration.MatchOutcome;
import org.example.pongrankbackend.Tournament.repository.TournamentMatchRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentParticipantRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.modelmapper.ModelMapper;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;

import java.util.*;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TournamentServiceImplTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long OUTSIDER_ID = 2L;
    private static final Long CLUB_ID = 10L;
    private static final Long TOURNAMENT_ID = 100L;

    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentParticipantRepository participantRepository;
    @Mock private TournamentMatchRepository tournamentMatchRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private ClubMembershipService clubMembershipService;
    @Mock private MatchIntegrationPort matchIntegrationPort;
    @Mock private ModelMapper modelMapper;
    @Spy private GroupDistributor groupDistributor = new GroupDistributor();
    @Spy private RoundRobinScheduler roundRobinScheduler = new RoundRobinScheduler();
    @Spy private GroupStandingsCalculator standingsCalculator = new GroupStandingsCalculator();
    @Spy private KnockoutBracketBuilder bracketBuilder = new KnockoutBracketBuilder();

    @InjectMocks
    private TournamentServiceImpl tournamentService;

    private Player player(Long id, double rating) {
        return Player.builder().id(id).name("Jugador " + id).ratingGlicko(rating).status(PlayerStatus.ACTIVE).build();
    }

    private Club club(ClubStatus status) {
        return Club.builder().id(CLUB_ID).name("Club Lima").admin(player(ADMIN_ID, 1500)).status(status).build();
    }

    private Tournament tournament(TournamentType type, TournamentStatus status) {
        return Tournament.builder()
                .id(TOURNAMENT_ID).name("Apertura").club(club(ClubStatus.APPROVED))
                .type(type).matchFormat(MatchFormat.BO3).status(status).build();
    }

    private TournamentParticipant participant(Tournament t, Player p, int seed, Integer group) {
        return TournamentParticipant.builder().id(p.getId() + 1000).tournament(t).player(p).seed(seed).groupNumber(group).build();
    }

    private TournamentMatch completed(Tournament t, int group, Player winner, Player loser) {
        return TournamentMatch.builder().tournament(t).stage(TournamentStage.GROUP).groupNumber(group)
                .player1(winner).player2(loser).winner(winner).status(TournamentMatchStatus.COMPLETED)
                .setsPlayer1(3).setsPlayer2(0).pointsPlayer1(33).pointsPlayer2(10).build();
    }

    private TournamentCreateRequestDTO createDto() {
        return TournamentCreateRequestDTO.builder().clubId(CLUB_ID).name("Apertura")
                .type(TournamentType.INTERNAL).matchFormat(MatchFormat.BO3).build();
    }

    private List<TournamentMatch> toList(Iterable<TournamentMatch> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }

    // ------------------------------------------------------------------ creation

    @Test
    @DisplayName("createTournament: un club PENDING o REJECTED no puede organizar torneos")
    void createTournament_ClubNotApproved_Throws() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.PENDING)));

        assertThatThrownBy(() -> tournamentService.createTournament(ADMIN_ID, createDto()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("APPROVED");
        verify(tournamentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTournament: solo el administrador del club puede crear torneos")
    void createTournament_NotClubAdmin_Throws() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.APPROVED)));

        assertThatThrownBy(() -> tournamentService.createTournament(OUTSIDER_ID, createDto()))
                .isInstanceOf(UnauthorizedActionException.class);
        verify(tournamentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTournament: crea el torneo OPEN vinculado al club y sin comunidad")
    void createTournament_Success() {
        Club club = club(ClubStatus.APPROVED);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(i -> i.getArgument(0));

        tournamentService.createTournament(ADMIN_ID, createDto());

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TournamentStatus.OPEN);
        assertThat(captor.getValue().getClub()).isEqualTo(club);
        assertThat(captor.getValue().getCommunity()).isNull();
    }

    // ------------------------------------------------------------------ participants

    @Test
    @DisplayName("addParticipant: un jugador INACTIVE o SUSPENDED no puede inscribirse")
    void addParticipant_InactivePlayer_Throws() {
        Player suspended = player(5L, 1500);
        suspended.setStatus(PlayerStatus.SUSPENDED);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(TournamentType.OPEN, TournamentStatus.OPEN)));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> tournamentService.addParticipant(TOURNAMENT_ID, ADMIN_ID, new TournamentParticipantRequestDTO(5L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ACTIVE");
        verify(participantRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("addParticipant: en un torneo INTERNAL solo se inscriben miembros activos del club")
    void addParticipant_InternalNonMember_Throws() {
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(TournamentType.INTERNAL, TournamentStatus.OPEN)));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player(5L, 1500)));
        when(clubMembershipService.isActiveMember(CLUB_ID, 5L)).thenReturn(false);

        assertThatThrownBy(() -> tournamentService.addParticipant(TOURNAMENT_ID, ADMIN_ID, new TournamentParticipantRequestDTO(5L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("INTERNAL");
    }

    @Test
    @DisplayName("addParticipant: en un torneo OPEN se inscribe cualquier ACTIVE y no se crea membresía; siembra por rating")
    void addParticipant_OpenTournament_SeedsByRating() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.OPEN);
        TournamentParticipant existing = participant(t, player(3L, 1500), 1, null);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player(5L, 1800)));
        when(participantRepository.findByTournamentIdOrderBySeedAsc(TOURNAMENT_ID)).thenReturn(List.of(existing));

        tournamentService.addParticipant(TOURNAMENT_ID, ADMIN_ID, new TournamentParticipantRequestDTO(5L));

        assertThat(existing.getSeed()).isEqualTo(2);
        verify(clubMembershipService, never()).isActiveMember(any(), any());
        verify(participantRepository).saveAll(any());
    }

    @Test
    @DisplayName("addParticipant: no permite participantes duplicados")
    void addParticipant_Duplicate_Throws() {
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(TournamentType.OPEN, TournamentStatus.OPEN)));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player(5L, 1500)));
        when(participantRepository.existsByTournamentIdAndPlayerId(TOURNAMENT_ID, 5L)).thenReturn(true);

        assertThatThrownBy(() -> tournamentService.addParticipant(TOURNAMENT_ID, ADMIN_ID, new TournamentParticipantRequestDTO(5L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya está inscrito");
    }

    @Test
    @DisplayName("addParticipant: la lista queda cerrada una vez iniciado el torneo")
    void addParticipant_TournamentStarted_Throws() {
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE)));

        assertThatThrownBy(() -> tournamentService.addParticipant(TOURNAMENT_ID, ADMIN_ID, new TournamentParticipantRequestDTO(5L)))
                .isInstanceOf(ConflictException.class);
        verify(playerRepository, never()).findById(any());
    }

    // ------------------------------------------------------------------ start

    @Test
    @DisplayName("startTournament: 5 participantes no permiten formar grupos de 3 o 4")
    void startTournament_FiveParticipants_Throws() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.OPEN);
        List<TournamentParticipant> five = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            five.add(participant(t, player(id + 10, 1500), (int) id, null));
        }
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(participantRepository.findByTournamentIdOrderBySeedAsc(TOURNAMENT_ID)).thenReturn(five);

        assertThatThrownBy(() -> tournamentService.startTournament(TOURNAMENT_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class);
        verify(matchIntegrationPort, never()).createMatch(any(), any(), any());
    }

    @Test
    @DisplayName("startTournament: no inicia si un participante dejó de estar ACTIVE")
    void startTournament_SuspendedParticipant_Throws() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.OPEN);
        List<TournamentParticipant> four = new ArrayList<>();
        for (long id = 1; id <= 4; id++) {
            four.add(participant(t, player(id + 10, 1500), (int) id, null));
        }
        four.get(2).getPlayer().setStatus(PlayerStatus.SUSPENDED);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(participantRepository.findByTournamentIdOrderBySeedAsc(TOURNAMENT_ID)).thenReturn(four);

        assertThatThrownBy(() -> tournamentService.startTournament(TOURNAMENT_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("retirarse");
        verify(matchIntegrationPort, never()).createMatch(any(), any(), any());
    }

    @Test
    @DisplayName("startTournament: 4 participantes forman 1 grupo con 6 partidos todos contra todos")
    void startTournament_FourParticipants_CreatesRoundRobin() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.OPEN);
        List<TournamentParticipant> four = new ArrayList<>();
        for (long id = 1; id <= 4; id++) {
            four.add(participant(t, player(id + 10, 1500), (int) id, null));
        }
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(participantRepository.findByTournamentIdOrderBySeedAsc(TOURNAMENT_ID)).thenReturn(four);
        when(matchIntegrationPort.createMatch(any(), any(), any())).thenAnswer(i -> new Match());
        when(tournamentRepository.save(t)).thenReturn(t);

        tournamentService.startTournament(TOURNAMENT_ID, ADMIN_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<TournamentMatch>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(tournamentMatchRepository).saveAll(captor.capture());
        List<TournamentMatch> fixtures = toList(captor.getValue());

        assertThat(t.getStatus()).isEqualTo(TournamentStatus.GROUP_STAGE);
        assertThat(fixtures).hasSize(6);
        assertThat(fixtures).allSatisfy(m -> {
            assertThat(m.getStatus()).isEqualTo(TournamentMatchStatus.SCHEDULED);
            assertThat(m.getGroupNumber()).isEqualTo(1);
        });
        assertThat(four).allSatisfy(p -> assertThat(p.getGroupNumber()).isEqualTo(1));
        verify(matchIntegrationPort, times(6)).createMatch(any(), any(), eq(MatchFormat.BO3));
    }

    // ------------------------------------------------------------------ results

    @Test
    @DisplayName("declareWalkover: el presente gana sin sets ni puntos inventados")
    void declareWalkover_Success() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE);
        Player p1 = player(11L, 1500);
        Player p2 = player(12L, 1500);
        TournamentMatch match = TournamentMatch.builder().id(500L).tournament(t).stage(TournamentStage.GROUP)
                .groupNumber(1).player1(p1).player2(p2).status(TournamentMatchStatus.SCHEDULED).build();
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(tournamentMatchRepository.findById(500L)).thenReturn(Optional.of(match));

        tournamentService.declareWalkover(TOURNAMENT_ID, 500L, ADMIN_ID, new TournamentWalkoverRequestDTO(11L));

        assertThat(match.getStatus()).isEqualTo(TournamentMatchStatus.WALKOVER);
        assertThat(match.getWinner()).isEqualTo(p2);
        assertThat(match.getSetsPlayer1()).isNull();
        assertThat(match.getPointsPlayer2()).isNull();
    }

    @Test
    @DisplayName("declareWalkover: cuando tiene un Match real vinculado, lo cierra como WALKOVER a través del puerto")
    void declareWalkover_WithRealMatch_ClosesMatchAsWalkover() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE);
        Player p1 = player(11L, 1500);
        Player p2 = player(12L, 1500);
        Match real = Match.builder().id(99L).build();
        TournamentMatch match = TournamentMatch.builder().id(500L).tournament(t).stage(TournamentStage.GROUP)
                .groupNumber(1).player1(p1).player2(p2).match(real).status(TournamentMatchStatus.SCHEDULED).build();
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(tournamentMatchRepository.findById(500L)).thenReturn(Optional.of(match));
        when(matchIntegrationPort.hasReportedScore(real)).thenReturn(false);

        tournamentService.declareWalkover(TOURNAMENT_ID, 500L, ADMIN_ID, new TournamentWalkoverRequestDTO(11L));

        assertThat(match.getStatus()).isEqualTo(TournamentMatchStatus.WALKOVER);
        assertThat(match.getWinner()).isEqualTo(p2);
        verify(matchIntegrationPort).closeAsWalkover(real, p2);
    }

    @Test
    @DisplayName("declareWalkover: no se permite si ya hay un marcador reportado en Match")
    void declareWalkover_ScoreAlreadyReported_Throws() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE);
        Match real = new Match();
        TournamentMatch match = TournamentMatch.builder().id(500L).tournament(t).stage(TournamentStage.GROUP)
                .groupNumber(1).player1(player(11L, 1500)).player2(player(12L, 1500)).match(real)
                .status(TournamentMatchStatus.SCHEDULED).build();
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(tournamentMatchRepository.findById(500L)).thenReturn(Optional.of(match));
        when(matchIntegrationPort.hasReportedScore(real)).thenReturn(true);

        assertThatThrownBy(() -> tournamentService.declareWalkover(TOURNAMENT_ID, 500L, ADMIN_ID, new TournamentWalkoverRequestDTO(11L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("marcador reportado");
        assertThat(match.getStatus()).isEqualTo(TournamentMatchStatus.SCHEDULED);
        assertThat(match.getWinner()).isNull();
    }

    @Test
    @DisplayName("syncMatchResults: rechaza un Match jugado por otros jugadores")
    void syncMatchResults_DifferentPlayers_Throws() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE);
        Match real = new Match();
        TournamentMatch match = TournamentMatch.builder().id(500L).tournament(t).stage(TournamentStage.GROUP)
                .groupNumber(1).player1(player(11L, 1500)).player2(player(12L, 1500)).match(real)
                .status(TournamentMatchStatus.SCHEDULED).build();
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(tournamentMatchRepository.findByTournamentIdOrderByIdAsc(TOURNAMENT_ID)).thenReturn(List.of(match));
        when(matchIntegrationPort.findConfirmedOutcome(real)).thenReturn(Optional.of(new MatchOutcome(11L, 99L, 11L, 2, 0, 22, 10)));

        assertThatThrownBy(() -> tournamentService.syncMatchResults(TOURNAMENT_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no coinciden");
        assertThat(match.getStatus()).isEqualTo(TournamentMatchStatus.SCHEDULED);
    }

    @Test
    @DisplayName("syncMatchResults: si el Match tiene a los jugadores en orden inverso, los sets se reorientan")
    void syncMatchResults_SwappedPlayers_Reoriented() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE);
        Player p1 = player(11L, 1500);
        Match real = new Match();
        TournamentMatch match = TournamentMatch.builder().id(500L).tournament(t).stage(TournamentStage.GROUP)
                .groupNumber(1).player1(p1).player2(player(12L, 1500)).match(real)
                .status(TournamentMatchStatus.SCHEDULED).build();
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(tournamentMatchRepository.findByTournamentIdOrderByIdAsc(TOURNAMENT_ID)).thenReturn(List.of(match));
        // In the Match, player1 is 12 and player2 is 11; player 11 wins 2-1
        when(matchIntegrationPort.findConfirmedOutcome(real)).thenReturn(Optional.of(new MatchOutcome(12L, 11L, 11L, 1, 2, 28, 31)));

        tournamentService.syncMatchResults(TOURNAMENT_ID, ADMIN_ID);

        assertThat(match.getStatus()).isEqualTo(TournamentMatchStatus.COMPLETED);
        assertThat(match.getWinner()).isEqualTo(p1);
        assertThat(match.getSetsPlayer1()).isEqualTo(2);
        assertThat(match.getSetsPlayer2()).isEqualTo(1);
        assertThat(match.getPointsPlayer1()).isEqualTo(31);
        assertThat(match.getPointsPlayer2()).isEqualTo(28);
    }

    @Test
    @DisplayName("syncMatchResults: rechaza un resultado cuyo ganador no juega el partido")
    void syncMatchResults_WinnerNotInMatch_Throws() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE);
        Match real = new Match();
        TournamentMatch match = TournamentMatch.builder().id(500L).tournament(t).stage(TournamentStage.GROUP)
                .groupNumber(1).player1(player(11L, 1500)).player2(player(12L, 1500)).match(real)
                .status(TournamentMatchStatus.SCHEDULED).build();
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(tournamentMatchRepository.findByTournamentIdOrderByIdAsc(TOURNAMENT_ID)).thenReturn(List.of(match));
        when(matchIntegrationPort.findConfirmedOutcome(real)).thenReturn(Optional.of(new MatchOutcome(11L, 12L, 999L, 2, 0, 22, 10)));

        assertThatThrownBy(() -> tournamentService.syncMatchResults(TOURNAMENT_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Resultado inválido");
        assertThat(match.getStatus()).isEqualTo(TournamentMatchStatus.SCHEDULED);
    }

    @Test
    @DisplayName("syncMatchResults: rechaza un resultado donde el ganador no tiene más sets")
    void syncMatchResults_WinnerWithoutMoreSets_Throws() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE);
        Match real = new Match();
        TournamentMatch match = TournamentMatch.builder().id(500L).tournament(t).stage(TournamentStage.GROUP)
                .groupNumber(1).player1(player(11L, 1500)).player2(player(12L, 1500)).match(real)
                .status(TournamentMatchStatus.SCHEDULED).build();
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(tournamentMatchRepository.findByTournamentIdOrderByIdAsc(TOURNAMENT_ID)).thenReturn(List.of(match));
        when(matchIntegrationPort.findConfirmedOutcome(real)).thenReturn(Optional.of(new MatchOutcome(11L, 12L, 11L, 1, 2, 30, 31)));

        assertThatThrownBy(() -> tournamentService.syncMatchResults(TOURNAMENT_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Llave: el ganador de una semifinal avanza a la final y la final se programa al tener ambos jugadores")
    void knockoutWinner_AdvancesAndSchedulesNextMatch() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.KNOCKOUT_STAGE);
        Player p1 = player(11L, 1500);
        Player p2 = player(12L, 1500);
        Player p4 = player(14L, 1500);
        Match semifinalMatch = new Match();
        TournamentMatch semifinal = TournamentMatch.builder().id(600L).tournament(t).stage(TournamentStage.KNOCKOUT)
                .round(1).bracketPosition(0).player1(p1).player2(p2).match(semifinalMatch)
                .status(TournamentMatchStatus.SCHEDULED).build();
        TournamentMatch finalMatch = TournamentMatch.builder().id(601L).tournament(t).stage(TournamentStage.KNOCKOUT)
                .round(2).bracketPosition(0).player2(p4).status(TournamentMatchStatus.PENDING_PLAYERS).build();

        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(tournamentMatchRepository.findByTournamentIdOrderByIdAsc(TOURNAMENT_ID)).thenReturn(List.of(semifinal, finalMatch));
        when(matchIntegrationPort.findConfirmedOutcome(any())).thenAnswer(i -> i.getArgument(0) == semifinalMatch
                ? Optional.of(new MatchOutcome(11L, 12L, 11L, 2, 1, 30, 25))
                : Optional.empty());
        when(tournamentMatchRepository.findByTournamentIdAndStageAndRoundAndBracketPosition(TOURNAMENT_ID, TournamentStage.KNOCKOUT, 2, 0))
                .thenReturn(Optional.of(finalMatch));
        when(matchIntegrationPort.createMatch(p1, p4, MatchFormat.BO3)).thenReturn(new Match());

        tournamentService.syncMatchResults(TOURNAMENT_ID, ADMIN_ID);

        assertThat(semifinal.getStatus()).isEqualTo(TournamentMatchStatus.COMPLETED);
        assertThat(semifinal.getWinner()).isEqualTo(p1);
        assertThat(finalMatch.getPlayer1()).isEqualTo(p1);
        assertThat(finalMatch.getStatus()).isEqualTo(TournamentMatchStatus.SCHEDULED);
        assertThat(t.getStatus()).isEqualTo(TournamentStatus.KNOCKOUT_STAGE);
    }

    @Test
    @DisplayName("Llave: al confirmarse la final el torneo termina con su campeón")
    void finalResult_FinishesTournament() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.KNOCKOUT_STAGE);
        Player p1 = player(11L, 1500);
        Player p2 = player(12L, 1500);
        Match real = new Match();
        TournamentMatch finalMatch = TournamentMatch.builder().id(700L).tournament(t).stage(TournamentStage.KNOCKOUT)
                .round(1).bracketPosition(0).player1(p1).player2(p2).match(real)
                .status(TournamentMatchStatus.SCHEDULED).build();
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(tournamentMatchRepository.findByTournamentIdOrderByIdAsc(TOURNAMENT_ID)).thenReturn(List.of(finalMatch));
        when(matchIntegrationPort.findConfirmedOutcome(real)).thenReturn(Optional.of(new MatchOutcome(11L, 12L, 12L, 1, 3, 40, 44)));

        tournamentService.syncMatchResults(TOURNAMENT_ID, ADMIN_ID);

        assertThat(t.getStatus()).isEqualTo(TournamentStatus.FINISHED);
        assertThat(t.getWinner()).isEqualTo(p2);
    }

    // ------------------------------------------------------------------ knockout generation

    @Test
    @DisplayName("generateKnockout: no se genera con partidos de grupo pendientes")
    void generateKnockout_GroupsNotFinished_Throws() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE);
        Player a = player(11L, 1500);
        Player b = player(12L, 1500);
        Player c = player(13L, 1500);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(participantRepository.findByTournamentIdOrderBySeedAsc(TOURNAMENT_ID)).thenReturn(List.of(
                participant(t, a, 1, 1), participant(t, b, 2, 1), participant(t, c, 3, 1)));
        TournamentMatch pending = TournamentMatch.builder().tournament(t).stage(TournamentStage.GROUP).groupNumber(1)
                .player1(a).player2(b).status(TournamentMatchStatus.SCHEDULED).build();
        when(tournamentMatchRepository.findByTournamentIdAndStage(TOURNAMENT_ID, TournamentStage.GROUP))
                .thenReturn(List.of(pending, completed(t, 1, a, c), completed(t, 1, b, c)));

        assertThatThrownBy(() -> tournamentService.generateKnockout(TOURNAMENT_ID, ADMIN_ID, false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("finalizados");
    }

    @Test
    @DisplayName("generateKnockout: 2 grupos de 3 generan semifinales cruzadas (sin rivales del mismo grupo) y la final")
    void generateKnockout_TwoGroups_CrossedSemifinals() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE);
        Player s1 = player(1L, 1500), s2 = player(2L, 1500), s3 = player(3L, 1500);
        Player s4 = player(4L, 1500), s5 = player(5L, 1500), s6 = player(6L, 1500);
        // Snake for 6: group 1 = seeds 1, 4, 5; group 2 = seeds 2, 3, 6
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(participantRepository.findByTournamentIdOrderBySeedAsc(TOURNAMENT_ID)).thenReturn(List.of(
                participant(t, s1, 1, 1), participant(t, s2, 2, 2), participant(t, s3, 3, 2),
                participant(t, s4, 4, 1), participant(t, s5, 5, 1), participant(t, s6, 6, 2)));
        when(tournamentMatchRepository.findByTournamentIdAndStage(TOURNAMENT_ID, TournamentStage.GROUP)).thenReturn(List.of(
                completed(t, 1, s1, s4), completed(t, 1, s1, s5), completed(t, 1, s4, s5),
                completed(t, 2, s2, s3), completed(t, 2, s2, s6), completed(t, 2, s3, s6)));
        when(matchIntegrationPort.createMatch(any(), any(), any())).thenAnswer(i -> new Match());
        when(tournamentRepository.save(t)).thenReturn(t);

        tournamentService.generateKnockout(TOURNAMENT_ID, ADMIN_ID, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<TournamentMatch>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(tournamentMatchRepository).saveAll(captor.capture());
        List<TournamentMatch> bracket = toList(captor.getValue());

        assertThat(t.getStatus()).isEqualTo(TournamentStatus.KNOCKOUT_STAGE);
        assertThat(bracket).hasSize(3);
        TournamentMatch semi1 = bracket.get(0);
        TournamentMatch semi2 = bracket.get(1);
        TournamentMatch finalMatch = bracket.get(2);
        assertThat(semi1.getPlayer1()).isEqualTo(s1);
        assertThat(semi1.getPlayer2()).isEqualTo(s3);
        assertThat(semi2.getPlayer1()).isEqualTo(s2);
        assertThat(semi2.getPlayer2()).isEqualTo(s4);
        assertThat(semi1.getStatus()).isEqualTo(TournamentMatchStatus.SCHEDULED);
        assertThat(finalMatch.getRound()).isEqualTo(2);
        assertThat(finalMatch.getStatus()).isEqualTo(TournamentMatchStatus.PENDING_PLAYERS);
        verify(matchIntegrationPort, times(2)).createMatch(any(), any(), any());
    }

    @Test
    @DisplayName("generateKnockout: con un solo grupo informa del cruce inevitable antes de generar la llave")
    void generateKnockout_OneGroup_RequiresConfirmation() {
        Tournament t = tournament(TournamentType.OPEN, TournamentStatus.GROUP_STAGE);
        Player a = player(11L, 1500), b = player(12L, 1500), c = player(13L, 1500);
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(t));
        when(participantRepository.findByTournamentIdOrderBySeedAsc(TOURNAMENT_ID)).thenReturn(List.of(
                participant(t, a, 1, 1), participant(t, b, 2, 1), participant(t, c, 3, 1)));
        when(tournamentMatchRepository.findByTournamentIdAndStage(TOURNAMENT_ID, TournamentStage.GROUP)).thenReturn(List.of(
                completed(t, 1, a, b), completed(t, 1, a, c), completed(t, 1, b, c)));

        assertThatThrownBy(() -> tournamentService.generateKnockout(TOURNAMENT_ID, ADMIN_ID, false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("allowSameGroupMatches");
        verify(tournamentMatchRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("getClubTournaments: pagina en la base de datos los torneos del club, del más reciente al más antiguo")
    void getClubTournaments_PaginatesInDatabase() {
        when(clubRepository.existsById(CLUB_ID)).thenReturn(true);
        when(tournamentRepository.findByClubId(eq(CLUB_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tournament(TournamentType.OPEN, TournamentStatus.OPEN)), PageRequest.of(0, 10), 12));

        PageResponseDTO<TournamentResponseDTO> result = tournamentService.getClubTournaments(CLUB_ID, 0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tournamentRepository).findByClubId(eq(CLUB_ID), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("createdAt").descending());
        assertThat(result.getTotalElements()).isEqualTo(12);
        assertThat(result.getTotalPages()).isEqualTo(2);
        verify(tournamentRepository, never()).findAll();
    }

    @Test
    @DisplayName("getClubTournaments: club inexistente no consulta torneos")
    void getClubTournaments_UnknownClub_Throws() {
        when(clubRepository.existsById(CLUB_ID)).thenReturn(false);

        assertThatThrownBy(() -> tournamentService.getClubTournaments(CLUB_ID, 0, 10))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(tournamentRepository, never()).findByClubId(any(), any());
    }
}
