package org.example.pongrankbackend.TrainingSession.mapper;

import org.example.pongrankbackend.TrainingSession.TrainingSession;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionCreateDTO;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionResponseDTO;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
public class TrainingSessionMapper {

    private final ModelMapper modelMapper;

    public TrainingSessionMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public TrainingSession toEntity(TrainingSessionCreateDTO dto) {
        return modelMapper.map(dto, TrainingSession.class);
    }

    public TrainingSessionResponseDTO toDto(TrainingSession trainingSession) {
        return modelMapper.map(trainingSession, TrainingSessionResponseDTO.class);
    }
}
