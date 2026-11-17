package org.example.pongrankbackend.Tournament.service;

import lombok.RequiredArgsConstructor;
import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.Tournament.*;
import org.example.pongrankbackend.Tournament.dto.DirectTournamentRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.repository.TournamentMatchRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentParticipantRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DirectTournamentService {
    private final ClubRepository clubs;
    private final PlayerRepository players;
    private final TournamentRepository tournaments;
    private final TournamentParticipantRepository participants;
    private final TournamentMatchRepository matches;
    private final TournamentAccessPolicy access;
    private final TournamentMapper mapper;
    private final TournamentMatchScheduler scheduler;

    @Transactional
    public TournamentResponseDTO publish(DirectTournamentRequestDTO request) {
        Club club = clubs.findByIdForUpdate(request.clubId())
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado"));
        access.validateClubAdmin(club, SecurityUtils.getRequiredCurrentUserId());
        Optional<Tournament> published = tournaments.findByClubIdAndPublicationKey(club.getId(), request.publicationKey());
        if (published.isPresent()) {
            return mapper.toResponse(published.get());
        }
        if (!club.canOrganizeTournaments()) {
            throw new ConflictException("Solo un club activo y aprobado puede organizar torneos");
        }
        int count = request.playerIds().size();
        if (count < 4 || count % 4 != 0) {
            throw new ConflictException("Selecciona al menos 4 participantes, en múltiplos de 4");
        }
        List<Player> ranked = players.findAllById(new HashSet<>(request.playerIds()));
        if (ranked.size() != count) {
            throw new ConflictException("Selecciona jugadores distintos que pertenezcan al club");
        }
        ranked.sort(Comparator.comparing(Player::getRatingGlicko).reversed().thenComparing(Player::getId));

        Tournament tournament = Tournament.builder()
                .club(club)
                .name(request.name().strip())
                .type(TournamentType.INTERNAL)
                .matchFormat(request.matchFormat())
                .startDate(LocalDateTime.ofInstant(request.scheduledAt(), ZoneId.of("America/Lima")))
                .publicationKey(request.publicationKey())
                .status(TournamentStatus.KNOCKOUT_STAGE)
                .build();
        for (Player player : ranked) {
            String error = access.eligibilityError(tournament, player);
            if (error != null) throw new ConflictException(error);
        }
        tournaments.save(tournament);

        List<TournamentParticipant> registrations = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            registrations.add(TournamentParticipant.builder().tournament(tournament).player(ranked.get(i)).seed(i + 1).build());
        }
        participants.saveAll(registrations);

        List<TournamentMatch> fixtures = fixtures(tournament, ranked);
        for (TournamentMatch fixture : fixtures) {
            if (fixture.getPlayer1() != null && fixture.getPlayer2() != null) {
                scheduler.schedule(tournament, fixture);
            }
        }
        matches.saveAll(fixtures);
        return mapper.toResponse(tournament);
    }

    static List<TournamentMatch> fixtures(Tournament tournament, List<Player> ranked) {
        int principal = Integer.highestOneBit(ranked.size());
        int direct = 2 * principal - ranked.size();
        int firstRound = direct == principal ? 1 : 2;
        List<Integer> order = seedOrder(principal);
        List<TournamentMatch> fixtures = new ArrayList<>();
        for (int position = 0; position < principal / 2; position++) {
            TournamentMatch main = fixture(tournament, firstRound, position);
            for (int side = 0; side < 2; side++) {
                int slot = position * 2 + side;
                int seed = order.get(slot);
                if (seed > direct) {
                    TournamentMatch preliminary = fixture(tournament, 1, slot);
                    preliminary.setPlayer1(ranked.get(seed - 1));
                    preliminary.setPlayer2(ranked.get(ranked.size() - (seed - direct)));
                    fixtures.add(preliminary);
                } else if (side == 0) {
                    main.setPlayer1(ranked.get(seed - 1));
                } else {
                    main.setPlayer2(ranked.get(seed - 1));
                }
            }
            fixtures.add(main);
        }
        for (int count = principal / 4, round = firstRound + 1; count > 0; count /= 2, round++) {
            for (int position = 0; position < count; position++) {
                fixtures.add(fixture(tournament, round, position));
            }
        }
        return fixtures;
    }

    private static TournamentMatch fixture(Tournament tournament, int round, int position) {
        return TournamentMatch.builder().tournament(tournament).stage(TournamentStage.KNOCKOUT)
                .round(round).bracketPosition(position).build();
    }

    private static List<Integer> seedOrder(int size) {
        if (size == 16) return List.of(1, 16, 8, 9, 5, 12, 4, 13, 3, 14, 6, 11, 7, 10, 2, 15);
        List<Integer> order = List.of(1, 2);
        while (order.size() < size) {
            int sum = 2 * order.size() + 1;
            List<Integer> next = new ArrayList<>();
            for (int seed : order) {
                next.add(seed);
                next.add(sum - seed);
            }
            order = next;
        }
        return order;
    }
}
