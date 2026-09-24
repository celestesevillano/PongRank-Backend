package org.example.pongrankbackend.MatchSet.service;

import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.MatchSet.MatchSet;
import org.example.pongrankbackend.MatchSet.dto.MatchSetResponseDTO;
import org.example.pongrankbackend.MatchSet.repository.MatchSetRepository;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MatchSetServiceImpl implements MatchSetService {

    private final MatchSetRepository matchSetRepository;
    private final MatchRepository matchRepository;

    public MatchSetServiceImpl(MatchSetRepository matchSetRepository,
                               MatchRepository matchRepository) {
        this.matchSetRepository = matchSetRepository;
        this.matchRepository = matchRepository;
    }

    @Override
    public List<MatchSetResponseDTO> getSetsByMatchId(Long matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new ResourceNotFoundException("Partido no encontrado con ID: " + matchId);
        }

        List<MatchSet> sets = matchSetRepository.findByMatchIdOrderBySetNumberAsc(matchId);
        return sets.stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    public MatchSetResponseDTO getSetById(Long setId) {
        MatchSet set = matchSetRepository.findById(setId)
                .orElseThrow(() -> new ResourceNotFoundException("Set no encontrado con ID: " + setId));
        return toResponseDTO(set);
    }

    private MatchSetResponseDTO toResponseDTO(MatchSet set) {
        return MatchSetResponseDTO.builder()
                .id(set.getId())
                .setNumber(set.getSetNumber())
                .scorePlayer1(set.getScorePlayer1())
                .scorePlayer2(set.getScorePlayer2())
                .winnerPlayerNumber(set.getScorePlayer1() > set.getScorePlayer2() ? 1 : 2)
                .build();
    }
}
