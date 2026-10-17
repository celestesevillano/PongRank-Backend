package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.ClubMembership.service.ClubMembershipService;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentStatus;
import org.example.pongrankbackend.Tournament.TournamentType;
import org.example.pongrankbackend.Tournament.repository.TournamentRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.springframework.stereotype.Component;

@Component
public class TournamentAccessPolicy {

    private final TournamentRepository tournamentRepository;
    private final ClubMembershipService clubMembershipService;

    public TournamentAccessPolicy(TournamentRepository tournamentRepository,
                                  ClubMembershipService clubMembershipService) {
        this.tournamentRepository = tournamentRepository;
        this.clubMembershipService = clubMembershipService;
    }

    public Tournament findTournament(Long tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado con ID: " + tournamentId));
    }

    // Tournaments are managed by the current admin of the organizing club
    public Tournament findManagedTournament(Long tournamentId, Long actingPlayerId) {
        Tournament tournament = findTournament(tournamentId);
        validateClubAdmin(tournament.getClub(), actingPlayerId);
        return tournament;
    }

    public void validateClubAdmin(Club club, Long actingPlayerId) {
        if (!club.getAdmin().getId().equals(actingPlayerId)) {
            throw new UnauthorizedActionException("Solo el administrador del club organizador puede gestionar sus torneos");
        }
    }

    public void validateStatus(Tournament tournament, TournamentStatus expected, String message) {
        if (tournament.getStatus() != expected) {
            throw new ConflictException(message);
        }
    }

    public void validateInProgress(Tournament tournament) {
        if (tournament.getStatus() != TournamentStatus.GROUP_STAGE && tournament.getStatus() != TournamentStatus.KNOCKOUT_STAGE) {
            throw new ConflictException("El torneo no está en juego");
        }
    }

    // Returns the reason why a player cannot take part, or null if eligible
    public String eligibilityError(Tournament tournament, Player player) {
        if (player.getStatus() != PlayerStatus.ACTIVE) {
            return "Solo los jugadores ACTIVE pueden participar en torneos (" + player.getName() + " está " + player.getStatus() + ")";
        }
        if (tournament.getType() == TournamentType.INTERNAL
                && !clubMembershipService.isActiveMember(tournament.getClub().getId(), player.getId())) {
            return "En un torneo INTERNAL solo participan miembros activos del club (" + player.getName() + " no lo es)";
        }
        return null;
    }
}
