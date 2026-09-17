package org.example.pongrankbackend.Player.mapper;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PlayerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shareContact", defaultValue = "false")
    @Mapping(target = "federatedDeclared", defaultValue = "false")
    @Mapping(target = "ratingGlicko", ignore = true)
    @Mapping(target = "ratingDeviation", ignore = true)
    @Mapping(target = "volatility", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "memberships", ignore = true)
    @Mapping(target = "trainingSessions", ignore = true)
    @Mapping(target = "matchesAsPlayer1", ignore = true)
    @Mapping(target = "matchesAsPlayer2", ignore = true)
    @Mapping(target = "sentFriendships", ignore = true)
    @Mapping(target = "receivedFriendships", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Player toEntity(PlayerRegisterRequestDTO dto);

    PlayerResponseDTO toDto(Player player);

    PlayerSummaryDTO toSummaryDto(Player player);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "ratingGlicko", ignore = true)
    @Mapping(target = "ratingDeviation", ignore = true)
    @Mapping(target = "volatility", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "memberships", ignore = true)
    @Mapping(target = "trainingSessions", ignore = true)
    @Mapping(target = "matchesAsPlayer1", ignore = true)
    @Mapping(target = "matchesAsPlayer2", ignore = true)
    @Mapping(target = "sentFriendships", ignore = true)
    @Mapping(target = "receivedFriendships", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(PlayerUpdateRequestDTO dto, @MappingTarget Player player);
}
