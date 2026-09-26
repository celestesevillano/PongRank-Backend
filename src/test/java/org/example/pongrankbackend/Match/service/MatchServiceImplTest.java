package org.example.pongrankbackend.Match.service;

import jakarta.persistence.EntityManager;
import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.example.pongrankbackend.Friendship.repository.FriendshipRepository;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.MatchType;
import org.example.pongrankbackend.Match.dto.*;
import org.example.pongrankbackend.Match.event.MatchConfirmedEvent;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.Match.websocket.MatchWebSocketNotifier;
import org.example.pongrankbackend.MatchSet.MatchSet;
import org.example.pongrankbackend.MatchSet.dto.MatchSetRequestDTO;
import org.example.pongrankbackend.MatchSet.repository.MatchSetRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceImplTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchSetRepository matchSetRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private MatchRuleValidator matchRuleValidator;

    @Mock
    private MatchWebSocketNotifier webSocketNotifier;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private MatchServiceImpl matchService;

    private Player createPlayer(Long id, String name) {
        return Player.builder()
                .id(id)
                .name(name)
                .email(name.toLowerCase() + "@pongrank.com")
                .ratingGlicko(1500.0)
                .ratingDeviation(350.0)
                .volatility(0.06)
                .build();
    }

    private void stubModelMapper(Player player) {
        when(modelMapper.map(player, PlayerSummaryDTO.class)).thenReturn(
                PlayerSummaryDTO.builder().id(player.getId()).name(player.getName()).build()
        );
    }

    @Test
    @DisplayName("shouldCreateMatchWhenValidFriendRequest")
    void shouldCreateMatchWhenValidFriendRequest() {
        // Arrange
        Long creatorId = 1L;
        Long opponentId = 2L;
        Player creator = createPlayer(creatorId, "Alice");
        Player opponent = createPlayer(opponentId, "Bob");

        MatchCreateRequestDTO dto = MatchCreateRequestDTO.builder()
                .opponentId(opponentId)
                .matchType(MatchType.FRIEND)
                .format(MatchFormat.BO3)
                .build();

        Friendship friendship = Friendship.builder().status(FriendshipStatus.ACCEPTED).build();

        when(playerRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(playerRepository.findById(opponentId)).thenReturn(Optional.of(opponent));
        when(friendshipRepository.findFriendshipBetween(creatorId, opponentId)).thenReturn(Optional.of(friendship));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(10L);
            return m;
        });
        stubModelMapper(creator);
        stubModelMapper(opponent);

        // Act
        MatchResponseDTO result = matchService.createMatch(creatorId, dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getMatchType()).isEqualTo(MatchType.FRIEND);
        assertThat(result.getStatus()).isEqualTo(MatchStatus.CREATED);

        verify(matchRuleValidator).validateMatchCreation(creator, opponent, dto, Optional.of(friendship));
        verify(webSocketNotifier).notifyMatchCreated(any(Match.class), eq(result));
    }

    @Test
    @DisplayName("getOpenLocationMatchesNearby: excluye partidos propios y de nivel muy distinto")
    void getOpenLocationMatchesNearby_FiltersOwnAndOutOfRatingRange() {
        Long requesterId = 1L;
        Player requester = createPlayer(requesterId, "Alice");
        requester.setRatingGlicko(1500.0);

        Player similarCreator = createPlayer(2L, "Bob");
        similarCreator.setRatingGlicko(1550.0);
        Match similarMatch = Match.builder().id(20L).player1(similarCreator).matchType(MatchType.LOCATION)
                .status(MatchStatus.CREATED)
                .latitude(new java.math.BigDecimal("-12.0")).longitude(new java.math.BigDecimal("-77.0")).build();

        Player farRatingCreator = createPlayer(3L, "Carol");
        farRatingCreator.setRatingGlicko(1500.0 + MatchRuleValidator.MAX_LOCATION_RATING_GAP + 50);
        Match farRatingMatch = Match.builder().id(21L).player1(farRatingCreator).matchType(MatchType.LOCATION)
                .status(MatchStatus.CREATED)
                .latitude(new java.math.BigDecimal("-12.0")).longitude(new java.math.BigDecimal("-77.0")).build();

        Match ownMatch = Match.builder().id(22L).player1(requester).matchType(MatchType.LOCATION)
                .status(MatchStatus.CREATED)
                .latitude(new java.math.BigDecimal("-12.0")).longitude(new java.math.BigDecimal("-77.0")).build();

        when(playerRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(matchRepository.findOpenChallenges(MatchStatus.CREATED, MatchType.LOCATION))
                .thenReturn(List.of(similarMatch, farRatingMatch, ownMatch));
        stubModelMapper(similarCreator);

        List<MatchResponseDTO> result = matchService.getOpenLocationMatchesNearby(
                requesterId, new java.math.BigDecimal("-12.0"), new java.math.BigDecimal("-77.0"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("joinOpenMatch: une al jugador como player2 y notifica al creador")
    void joinOpenMatch_Success_SetsPlayer2AndNotifies() {
        Long joinerId = 2L;
        Player creator = createPlayer(1L, "Alice");
        Player joiner = createPlayer(joinerId, "Bob");
        Match openMatch = Match.builder().id(30L).player1(creator).matchType(MatchType.LOCATION)
                .status(MatchStatus.CREATED)
                .latitude(new java.math.BigDecimal("-12.0")).longitude(new java.math.BigDecimal("-77.0")).build();
        MatchJoinRequestDTO dto = MatchJoinRequestDTO.builder()
                .latitude(new java.math.BigDecimal("-12.001")).longitude(new java.math.BigDecimal("-77.001")).build();

        when(matchRepository.findById(30L)).thenReturn(Optional.of(openMatch));
        when(playerRepository.findById(joinerId)).thenReturn(Optional.of(joiner));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        stubModelMapper(creator);
        stubModelMapper(joiner);

        MatchResponseDTO result = matchService.joinOpenMatch(joinerId, 30L, dto);

        assertThat(result).isNotNull();
        assertThat(openMatch.getPlayer2()).isEqualTo(joiner);
        verify(matchRuleValidator).validateRatingGap(creator, joiner);
        verify(webSocketNotifier).notifyMatchJoined(eq(1L), eq(result));
    }

    @Test
    @DisplayName("joinOpenMatch: rechaza unirse a un partido que no es LOCATION")
    void joinOpenMatch_NotLocationType_ThrowsException() {
        Player creator = createPlayer(1L, "Alice");
        Match friendMatch = Match.builder().id(31L).player1(creator).matchType(MatchType.FRIEND)
                .status(MatchStatus.CREATED).build();
        when(matchRepository.findById(31L)).thenReturn(Optional.of(friendMatch));

        assertThatThrownBy(() -> matchService.joinOpenMatch(2L, 31L, MatchJoinRequestDTO.builder()
                .latitude(java.math.BigDecimal.ZERO).longitude(java.math.BigDecimal.ZERO).build()))
                .isInstanceOf(InvalidMatchStateException.class);

        verify(matchRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinOpenMatch: rechaza un partido que ya tiene oponente")
    void joinOpenMatch_AlreadyTaken_ThrowsException() {
        Player creator = createPlayer(1L, "Alice");
        Player existingOpponent = createPlayer(3L, "Carol");
        Match takenMatch = Match.builder().id(32L).player1(creator).player2(existingOpponent)
                .matchType(MatchType.LOCATION).status(MatchStatus.CREATED).build();
        when(matchRepository.findById(32L)).thenReturn(Optional.of(takenMatch));

        assertThatThrownBy(() -> matchService.joinOpenMatch(2L, 32L, MatchJoinRequestDTO.builder()
                .latitude(java.math.BigDecimal.ZERO).longitude(java.math.BigDecimal.ZERO).build()))
                .isInstanceOf(org.example.pongrankbackend.common.exception.ConflictException.class);

        verify(matchRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinOpenMatch: el creador no puede unirse a su propio partido")
    void joinOpenMatch_OwnMatch_ThrowsException() {
        Player creator = createPlayer(1L, "Alice");
        Match ownMatch = Match.builder().id(33L).player1(creator).matchType(MatchType.LOCATION)
                .status(MatchStatus.CREATED).build();
        when(matchRepository.findById(33L)).thenReturn(Optional.of(ownMatch));

        assertThatThrownBy(() -> matchService.joinOpenMatch(1L, 33L, MatchJoinRequestDTO.builder()
                .latitude(java.math.BigDecimal.ZERO).longitude(java.math.BigDecimal.ZERO).build()))
                .isInstanceOf(InvalidMatchStateException.class);

        verify(matchRepository, never()).save(any());
    }

    @Test
    @DisplayName("joinOpenMatch: rechaza si la distancia con el creador excede el máximo permitido")
    void joinOpenMatch_TooFar_ThrowsException() {
        Long joinerId = 2L;
        Player creator = createPlayer(1L, "Alice");
        Player joiner = createPlayer(joinerId, "Bob");
        Match openMatch = Match.builder().id(34L).player1(creator).matchType(MatchType.LOCATION)
                .status(MatchStatus.CREATED)
                .latitude(new java.math.BigDecimal("-12.0")).longitude(new java.math.BigDecimal("-77.0")).build();
        MatchJoinRequestDTO dto = MatchJoinRequestDTO.builder()
                .latitude(new java.math.BigDecimal("-13.0")).longitude(new java.math.BigDecimal("-78.0")).build();

        when(matchRepository.findById(34L)).thenReturn(Optional.of(openMatch));
        when(playerRepository.findById(joinerId)).thenReturn(Optional.of(joiner));
        when(matchRuleValidator.calculateDistanceKm(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(120.0);

        assertThatThrownBy(() -> matchService.joinOpenMatch(joinerId, 34L, dto))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessageContaining("distancia");

        verify(matchRepository, never()).save(any());
    }

    @Test
    @DisplayName("shouldCreateMatchWhenValidTournamentRequest")
    void shouldCreateMatchWhenValidTournamentRequest() {
        // Arrange
        Long creatorId = 1L;
        Long opponentId = 2L;
        Long tournamentId = 5L;
        Player creator = createPlayer(creatorId, "Alice");
        Player opponent = createPlayer(opponentId, "Bob");
        Tournament tournament = Tournament.builder().id(tournamentId).name("Torneo Primavera").build();

        MatchCreateRequestDTO dto = MatchCreateRequestDTO.builder()
                .opponentId(opponentId)
                .tournamentId(tournamentId)
                .matchType(MatchType.TOURNAMENT)
                .format(MatchFormat.BO5)
                .build();

        when(playerRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(playerRepository.findById(opponentId)).thenReturn(Optional.of(opponent));
        when(friendshipRepository.findFriendshipBetween(creatorId, opponentId)).thenReturn(Optional.empty());
        when(entityManager.find(Tournament.class, tournamentId)).thenReturn(tournament);
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(20L);
            return m;
        });
        stubModelMapper(creator);
        stubModelMapper(opponent);

        // Act
        MatchResponseDTO result = matchService.createMatch(creatorId, dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(20L);
        assertThat(result.getMatchType()).isEqualTo(MatchType.TOURNAMENT);
        assertThat(result.getTournamentId()).isEqualTo(tournamentId);
        assertThat(result.getTournamentName()).isEqualTo("Torneo Primavera");
    }

    @Test
    @DisplayName("shouldCreateTournamentMatchWhenInvokedDirectly")
    void shouldCreateTournamentMatchWhenInvokedDirectly() {
        // Arrange
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Tournament tournament = Tournament.builder().id(100L).name("Copa UTEC").build();

        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(50L);
            return m;
        });
        stubModelMapper(p1);
        stubModelMapper(p2);

        // Act
        Match match = matchService.createTournamentMatch(p1, p2, tournament, MatchFormat.BO5);

        // Assert
        assertThat(match).isNotNull();
        assertThat(match.getId()).isEqualTo(50L);
        assertThat(match.getMatchType()).isEqualTo(MatchType.TOURNAMENT);
        assertThat(match.getTournament()).isEqualTo(tournament);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.CREATED);

        verify(matchRepository).save(any(Match.class));
        verify(webSocketNotifier).notifyMatchCreated(eq(match), any(MatchResponseDTO.class));
    }

    @Test
    @DisplayName("shouldThrowResourceNotFoundExceptionWhenCreatorNotFound")
    void shouldThrowResourceNotFoundExceptionWhenCreatorNotFound() {
        // Arrange
        Long creatorId = 999L;
        MatchCreateRequestDTO dto = MatchCreateRequestDTO.builder().matchType(MatchType.FRIEND).build();

        when(playerRepository.findById(creatorId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> matchService.createMatch(creatorId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Jugador creador no encontrado");
    }

    @Test
    @DisplayName("shouldSubmitScoreWhenValidParticipantPlayer1")
    void shouldSubmitScoreWhenValidParticipantPlayer1() {
        // Arrange
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");

        Match match = Match.builder()
                .id(matchId)
                .player1(p1)
                .player2(p2)
                .format(MatchFormat.BO3)
                .status(MatchStatus.CREATED)
                .sets(new ArrayList<>())
                .build();

        List<MatchSetRequestDTO> setDtos = List.of(
                MatchSetRequestDTO.builder().setNumber(1).scorePlayer1(11).scorePlayer2(7).build(),
                MatchSetRequestDTO.builder().setNumber(2).scorePlayer1(11).scorePlayer2(9).build()
        );
        MatchScoreSubmitDTO submitDTO = MatchScoreSubmitDTO.builder().sets(setDtos).build();

        MatchRuleValidator.MatchValidationResult validationResult = MatchRuleValidator.MatchValidationResult.builder()
                .winnerPlayerNumber(1)
                .setsWonPlayer1(2)
                .setsWonPlayer2(0)
                .scoreSummary("2 - 0")
                .build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchRuleValidator.validateSetScoresAndDetermineWinner(MatchFormat.BO3, setDtos))
                .thenReturn(validationResult);
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        stubModelMapper(p1);
        stubModelMapper(p2);

        // Act
        MatchDetailResponseDTO result = matchService.submitScore(matchId, p1.getId(), submitDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(MatchStatus.PROPOSED_P1);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.PROPOSED_P1);
        assertThat(match.getSets()).hasSize(2);

        verify(webSocketNotifier).notifyScoreSubmitted(eq(matchId), eq(p2.getId()), any(MatchResponseDTO.class));
    }

    @Test
    @DisplayName("shouldThrowUnauthorizedActionExceptionWhenNonParticipantSubmitsScore")
    void shouldThrowUnauthorizedActionExceptionWhenNonParticipantSubmitsScore() {
        // Arrange
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match match = Match.builder().id(matchId).player1(p1).player2(p2).status(MatchStatus.CREATED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // Act & Assert
        Long intruderId = 999L;
        MatchScoreSubmitDTO dto = MatchScoreSubmitDTO.builder().sets(List.of()).build();

        assertThatThrownBy(() -> matchService.submitScore(matchId, intruderId, dto))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessageContaining("Solo un jugador participante");
    }

    @Test
    @DisplayName("shouldThrowInvalidMatchStateExceptionWhenSubmittingScoreOnConfirmedMatch")
    void shouldThrowInvalidMatchStateExceptionWhenSubmittingScoreOnConfirmedMatch() {
        // Arrange
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match match = Match.builder().id(matchId).player1(p1).player2(p2).status(MatchStatus.CONFIRMED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // Act & Assert
        MatchScoreSubmitDTO dto = MatchScoreSubmitDTO.builder().sets(List.of()).build();

        assertThatThrownBy(() -> matchService.submitScore(matchId, p1.getId(), dto))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessageContaining("ya fue confirmado y cerrado");
    }

    @Test
    @DisplayName("shouldConfirmMatchWhenOpponentConfirms")
    void shouldConfirmMatchWhenOpponentConfirms() {
        // Arrange
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");

        MatchSet set1 = MatchSet.builder().setNumber(1).scorePlayer1(11).scorePlayer2(8).build();
        MatchSet set2 = MatchSet.builder().setNumber(2).scorePlayer1(11).scorePlayer2(7).build();

        Match match = Match.builder()
                .id(matchId)
                .player1(p1)
                .player2(p2)
                .format(MatchFormat.BO3)
                .status(MatchStatus.PROPOSED_P1) // Propuesto por P1
                .sets(List.of(set1, set2))
                .build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        stubModelMapper(p1);
        stubModelMapper(p2);

        // Act: P2 (el rival) confirma
        MatchDetailResponseDTO result = matchService.confirmMatch(matchId, p2.getId());

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(result.getWinnerId()).isEqualTo(p1.getId());
        assertThat(match.getStatus()).isEqualTo(MatchStatus.CONFIRMED);

        verify(eventPublisher).publishEvent(any(MatchConfirmedEvent.class));
        verify(webSocketNotifier).notifyMatchConfirmed(eq(matchId), any(MatchResponseDTO.class));
    }

    @Test
    @DisplayName("shouldThrowUnauthorizedActionExceptionWhenProposerAttemptsToConfirmOwnScore")
    void shouldThrowUnauthorizedActionExceptionWhenProposerAttemptsToConfirmOwnScore() {
        // Arrange (Anti-trampa estricta de doble confirmación)
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");

        Match match = Match.builder()
                .id(matchId)
                .player1(p1)
                .player2(p2)
                .status(MatchStatus.PROPOSED_P1) // Propuesto por P1
                .build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // Act & Assert: P1 intenta auto-confirmar
        assertThatThrownBy(() -> matchService.confirmMatch(matchId, p1.getId()))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessageContaining("El jugador proponente no puede confirmar su propio resultado");
    }

    @Test
    @DisplayName("shouldDisputeMatchWhenOpponentDisputesWithReason")
    void shouldDisputeMatchWhenOpponentDisputesWithReason() {
        // Arrange
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");

        Match match = Match.builder()
                .id(matchId)
                .player1(p1)
                .player2(p2)
                .status(MatchStatus.PROPOSED_P1)
                .build();

        MatchDisputeRequestDTO disputeDTO = MatchDisputeRequestDTO.builder()
                .reason("El segundo set lo gané yo 11-9")
                .build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        stubModelMapper(p1);
        stubModelMapper(p2);

        // Act: P2 disputa
        MatchDetailResponseDTO result = matchService.disputeMatch(matchId, p2.getId(), disputeDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(MatchStatus.DISPUTED);
        assertThat(result.getDisputeReason()).isEqualTo("El segundo set lo gané yo 11-9");
        assertThat(match.getStatus()).isEqualTo(MatchStatus.DISPUTED);

        verify(webSocketNotifier).notifyMatchDisputed(eq(matchId), eq("El segundo set lo gané yo 11-9"), any(MatchResponseDTO.class));
    }

    @Test
    @DisplayName("shouldThrowUnauthorizedActionExceptionWhenProposerAttemptsToDisputeOwnScore")
    void shouldThrowUnauthorizedActionExceptionWhenProposerAttemptsToDisputeOwnScore() {
        // Arrange
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");

        Match match = Match.builder()
                .id(matchId)
                .player1(p1)
                .player2(p2)
                .status(MatchStatus.PROPOSED_P1)
                .build();

        MatchDisputeRequestDTO disputeDTO = MatchDisputeRequestDTO.builder().reason("Error").build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // Act & Assert
        assertThatThrownBy(() -> matchService.disputeMatch(matchId, p1.getId(), disputeDTO))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessageContaining("Solo el contrincante receptor puede disputar");
    }

    @Test
    @DisplayName("shouldCancelMatchWhenParticipantRequests")
    void shouldCancelMatchWhenParticipantRequests() {
        // Arrange
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");

        Match match = Match.builder()
                .id(matchId)
                .player1(p1)
                .player2(p2)
                .status(MatchStatus.CREATED)
                .build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        stubModelMapper(p1);
        stubModelMapper(p2);

        // Act
        MatchResponseDTO result = matchService.cancelMatch(matchId, p1.getId());

        // Assert
        assertThat(result.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        verify(matchRepository).save(any(Match.class));
    }

    @Test
    @DisplayName("shouldThrowUnauthorizedActionExceptionWhenNonParticipantCancels")
    void shouldThrowUnauthorizedActionExceptionWhenNonParticipantCancels() {
        // Arrange
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match match = Match.builder().id(matchId).player1(p1).player2(p2).status(MatchStatus.CREATED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // Act & Assert
        assertThatThrownBy(() -> matchService.cancelMatch(matchId, 999L))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessageContaining("Solo un jugador participante");
    }

    @Test
    @DisplayName("shouldGetMatchByIdWhenMatchExists")
    void shouldGetMatchByIdWhenMatchExists() {
        // Arrange
        Long matchId = 7L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match match = Match.builder().id(matchId).player1(p1).player2(p2).status(MatchStatus.CREATED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        stubModelMapper(p1);
        stubModelMapper(p2);

        // Act
        MatchDetailResponseDTO result = matchService.getMatchById(matchId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(matchId);
        verify(matchRepository).findById(matchId);
    }

    @Test
    @DisplayName("shouldThrowResourceNotFoundExceptionWhenMatchNotFound")
    void shouldThrowResourceNotFoundExceptionWhenMatchNotFound() {
        // Arrange
        Long matchId = 999L;
        when(matchRepository.findById(matchId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> matchService.getMatchById(matchId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Partido no encontrado con ID: 999");
    }

    @Test
    @DisplayName("shouldGetMatchesByPlayerWithPagination")
    void shouldGetMatchesByPlayerWithPagination() {
        // Arrange
        Long playerId = 1L;
        Player p1 = createPlayer(playerId, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match m1 = Match.builder().id(10L).player1(p1).player2(p2).status(MatchStatus.CONFIRMED).build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Match> page = new PageImpl<>(List.of(m1), pageable, 1);

        when(playerRepository.existsById(playerId)).thenReturn(true);
        when(matchRepository.findAllByPlayerId(playerId, pageable)).thenReturn(page);
        stubModelMapper(p1);
        stubModelMapper(p2);

        // Act
        Page<MatchResponseDTO> result = matchService.getMatchesByPlayer(playerId, pageable);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
        verify(matchRepository).findAllByPlayerId(playerId, pageable);
    }

    @Test
    @DisplayName("shouldGetMatchesByTournamentWithPagination")
    void shouldGetMatchesByTournamentWithPagination() {
        // Arrange
        Long tournamentId = 5L;
        Tournament tournament = Tournament.builder().id(tournamentId).name("Copa Pong").build();
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match m1 = Match.builder().id(30L).player1(p1).player2(p2).tournament(tournament).status(MatchStatus.CONFIRMED).build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Match> page = new PageImpl<>(List.of(m1), pageable, 1);

        when(entityManager.find(Tournament.class, tournamentId)).thenReturn(tournament);
        when(matchRepository.findByTournamentId(tournamentId, pageable)).thenReturn(page);
        stubModelMapper(p1);
        stubModelMapper(p2);

        // Act
        Page<MatchResponseDTO> result = matchService.getMatchesByTournament(tournamentId, pageable);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).getTournamentId()).isEqualTo(tournamentId);
        verify(matchRepository).findByTournamentId(tournamentId, pageable);
    }

    @Test
    @DisplayName("shouldCloseMatchAsWalkoverSuccessfully")
    void shouldCloseMatchAsWalkoverSuccessfully() {
        // Arrange
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match match = Match.builder().id(matchId).player1(p1).player2(p2).status(MatchStatus.CREATED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(p2));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        stubModelMapper(p1);
        stubModelMapper(p2);

        // Act
        MatchResponseDTO result = matchService.closeMatchAsWalkover(matchId, 2L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(MatchStatus.WALKOVER);
        assertThat(result.getWinnerId()).isEqualTo(2L);
        assertThat(result.getScoreSummary()).isEqualTo("W.O.");
        assertThat(result.getConfirmedAt()).isNotNull();

        assertThat(match.getStatus()).isEqualTo(MatchStatus.WALKOVER);
        assertThat(match.getWinner()).isEqualTo(p2);
        assertThat(match.getConfirmedAt()).isNotNull();

        verify(matchRepository).save(match);
        verify(webSocketNotifier).notifyMatchConfirmed(eq(matchId), any(MatchResponseDTO.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("shouldThrowResourceNotFoundExceptionWhenMatchNotFoundOnCloseMatchAsWalkover")
    void shouldThrowResourceNotFoundExceptionWhenMatchNotFoundOnCloseMatchAsWalkover() {
        Long matchId = 999L;
        when(matchRepository.findById(matchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.closeMatchAsWalkover(matchId, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Partido no encontrado con ID: 999");
    }

    @Test
    @DisplayName("shouldThrowInvalidMatchStateExceptionWhenMatchAlreadyConfirmedOnCloseWalkover")
    void shouldThrowInvalidMatchStateExceptionWhenMatchAlreadyConfirmedOnCloseWalkover() {
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match match = Match.builder().id(matchId).player1(p1).player2(p2).status(MatchStatus.CONFIRMED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchService.closeMatchAsWalkover(matchId, 1L))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessageContaining("ya fue confirmado y cerrado previamente");
    }

    @Test
    @DisplayName("shouldThrowInvalidMatchStateExceptionWhenMatchAlreadyCancelledOnCloseWalkover")
    void shouldThrowInvalidMatchStateExceptionWhenMatchAlreadyCancelledOnCloseWalkover() {
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match match = Match.builder().id(matchId).player1(p1).player2(p2).status(MatchStatus.CANCELLED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchService.closeMatchAsWalkover(matchId, 1L))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessageContaining("se encuentra cancelado");
    }

    @Test
    @DisplayName("shouldThrowInvalidMatchStateExceptionWhenMatchAlreadyWalkoverOnCloseWalkover")
    void shouldThrowInvalidMatchStateExceptionWhenMatchAlreadyWalkoverOnCloseWalkover() {
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match match = Match.builder().id(matchId).player1(p1).player2(p2).status(MatchStatus.WALKOVER).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchService.closeMatchAsWalkover(matchId, 1L))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessageContaining("ya fue cerrado por W.O.");
    }

    @Test
    @DisplayName("shouldThrowInvalidMatchStateExceptionWhenWinnerDoesNotParticipateInMatch")
    void shouldThrowInvalidMatchStateExceptionWhenWinnerDoesNotParticipateInMatch() {
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match match = Match.builder().id(matchId).player1(p1).player2(p2).status(MatchStatus.CREATED).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchService.closeMatchAsWalkover(matchId, 999L))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessageContaining("no participa en este partido");
    }

    @Test
    @DisplayName("shouldThrowInvalidMatchStateExceptionWhenSubmittingScoreOnWalkoverMatch")
    void shouldThrowInvalidMatchStateExceptionWhenSubmittingScoreOnWalkoverMatch() {
        Long matchId = 1L;
        Player p1 = createPlayer(1L, "Alice");
        Player p2 = createPlayer(2L, "Bob");
        Match match = Match.builder().id(matchId).player1(p1).player2(p2).status(MatchStatus.WALKOVER).build();

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchScoreSubmitDTO dto = MatchScoreSubmitDTO.builder().sets(List.of()).build();

        assertThatThrownBy(() -> matchService.submitScore(matchId, p1.getId(), dto))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessageContaining("cerrado por incomparecencia (W.O.)");
    }
}
