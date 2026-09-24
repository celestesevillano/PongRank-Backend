package org.example.pongrankbackend.Tournament;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.config.ModelMapperConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the team's real ModelMapper configuration to detect ambiguous or missing mappings
class TournamentMappingTest {

    private final ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    private final Player admin = Player.builder().id(1L).name("Ana Admin").build();
    private final Club club = Club.builder().id(10L).name("Club Lima").admin(admin).status(ClubStatus.APPROVED).build();
    private final Tournament tournament = Tournament.builder()
            .id(100L).name("Apertura").club(club).type(TournamentType.INTERNAL)
            .matchFormat(MatchFormat.BO5).status(TournamentStatus.OPEN).build();

    @Test
    @DisplayName("Tournament -> TournamentResponseDTO: mapea club organizador, tipo y formato")
    void mapsTournament() {
        TournamentResponseDTO dto = modelMapper.map(tournament, TournamentResponseDTO.class);

        assertThat(dto.getId()).isEqualTo(100L);
        assertThat(dto.getName()).isEqualTo("Apertura");
        assertThat(dto.getClubId()).isEqualTo(10L);
        assertThat(dto.getClubName()).isEqualTo("Club Lima");
        assertThat(dto.getType()).isEqualTo(TournamentType.INTERNAL);
        assertThat(dto.getMatchFormat()).isEqualTo(MatchFormat.BO5);
        assertThat(dto.getStatus()).isEqualTo(TournamentStatus.OPEN);
        assertThat(dto.getWinner()).isNull();
    }

    @Test
    @DisplayName("TournamentParticipant -> DTO: mapea jugador, siembra y grupo")
    void mapsParticipant() {
        TournamentParticipant participant = TournamentParticipant.builder()
                .id(5L).tournament(tournament).player(Player.builder().id(3L).name("Beto").build())
                .seed(2).groupNumber(1).build();

        TournamentParticipantResponseDTO dto = modelMapper.map(participant, TournamentParticipantResponseDTO.class);

        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getPlayer().getId()).isEqualTo(3L);
        assertThat(dto.getSeed()).isEqualTo(2);
        assertThat(dto.getGroupNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("TournamentMatch -> DTO: mapea matchId, jugadores y resultado; un BYE deja player2 en null")
    void mapsTournamentMatch() {
        Match match = new Match();
        match.setId(77L);
        TournamentMatch knockout = TournamentMatch.builder()
                .id(9L).tournament(tournament).stage(TournamentStage.KNOCKOUT).round(1).bracketPosition(0)
                .player1(Player.builder().id(3L).name("Beto").build()).match(match)
                .status(TournamentMatchStatus.BYE).build();

        TournamentMatchResponseDTO dto = modelMapper.map(knockout, TournamentMatchResponseDTO.class);

        assertThat(dto.getId()).isEqualTo(9L);
        assertThat(dto.getMatchId()).isEqualTo(77L);
        assertThat(dto.getPlayer1().getId()).isEqualTo(3L);
        assertThat(dto.getPlayer2()).isNull();
        assertThat(dto.getStatus()).isEqualTo(TournamentMatchStatus.BYE);
        assertThat(dto.getRound()).isEqualTo(1);
    }
}
