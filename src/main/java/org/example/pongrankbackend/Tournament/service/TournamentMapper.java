package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentParticipant;
import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
public class TournamentMapper {

    private final ModelMapper modelMapper;

    public TournamentMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public TournamentResponseDTO toResponse(Tournament tournament) {
        return modelMapper.map(tournament, TournamentResponseDTO.class);
    }

    public TournamentParticipantResponseDTO toParticipantResponse(TournamentParticipant participant) {
        return modelMapper.map(participant, TournamentParticipantResponseDTO.class);
    }

    public TournamentMatchResponseDTO toMatchResponse(TournamentMatch match) {
        return modelMapper.map(match, TournamentMatchResponseDTO.class);
    }
}
