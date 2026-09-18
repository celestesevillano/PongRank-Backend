package org.example.pongrankbackend.Friendship.mapper;

import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.dto.FriendshipResponseDTO;
import org.example.pongrankbackend.Player.mapper.PlayerMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {PlayerMapper.class})
public interface FriendshipMapper {

    FriendshipResponseDTO toDto(Friendship friendship);
}
