package org.example.pongrankbackend.Friendship.service;

import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.example.pongrankbackend.Friendship.dto.FriendshipRequestDTO;
import org.example.pongrankbackend.Friendship.dto.FriendshipResponseDTO;
import org.example.pongrankbackend.Friendship.repository.FriendshipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.FriendshipRequestException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final PlayerRepository playerRepository;
    private final ModelMapper modelMapper;

    public FriendshipServiceImpl(FriendshipRepository friendshipRepository,
                                  PlayerRepository playerRepository,
                                  ModelMapper modelMapper) {
        this.friendshipRepository = friendshipRepository;
        this.playerRepository = playerRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    @Transactional
    public FriendshipResponseDTO sendFriendRequest(Long senderId, FriendshipRequestDTO dto) {
        Long receiverId = dto.getReceiverId();

        if (senderId.equals(receiverId)) {
            throw new FriendshipRequestException("Un jugador no puede enviarse una solicitud de amistad a sí mismo");
        }

        Player sender = playerRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador emisor no encontrado con ID: " + senderId));

        Player receiver = playerRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador receptor no encontrado con ID: " + receiverId));

        if (friendshipRepository.existsFriendshipBetween(senderId, receiverId)) {
            throw new ConflictException("Ya existe una relación de amistad o solicitud pendiente entre ambos jugadores");
        }

        Friendship friendship = Friendship.builder()
                .playerA(sender)
                .playerB(receiver)
                .status(FriendshipStatus.PENDING)
                .build();

        Friendship savedFriendship = friendshipRepository.save(friendship);
        return modelMapper.map(savedFriendship, FriendshipResponseDTO.class);
    }

    @Override
    @Transactional
    public FriendshipResponseDTO acceptFriendRequest(Long friendshipId, Long actingPlayerId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de amistad no encontrada con ID: " + friendshipId));

        if (!friendship.getPlayerB().getId().equals(actingPlayerId)) {
            throw new UnauthorizedActionException("Solo el receptor original puede aceptar la solicitud de amistad");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ConflictException("La solicitud de amistad ya fue procesada o no está en estado PENDING");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        Friendship updatedFriendship = friendshipRepository.save(friendship);
        return modelMapper.map(updatedFriendship, FriendshipResponseDTO.class);
    }

    @Override
    @Transactional
    public FriendshipResponseDTO rejectFriendRequest(Long friendshipId, Long actingPlayerId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de amistad no encontrada con ID: " + friendshipId));

        if (!friendship.getPlayerB().getId().equals(actingPlayerId)) {
            throw new UnauthorizedActionException("Solo el receptor original puede rechazar la solicitud de amistad");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ConflictException("La solicitud de amistad ya fue procesada o no está en estado PENDING");
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        Friendship updatedFriendship = friendshipRepository.save(friendship);
        return modelMapper.map(updatedFriendship, FriendshipResponseDTO.class);
    }

    @Override
    public List<FriendshipResponseDTO> getAcceptedFriends(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }

        List<Friendship> friendships = friendshipRepository.findAllByPlayerIdAndStatus(playerId, FriendshipStatus.ACCEPTED);
        return friendships.stream()
                .map(friendship -> modelMapper.map(friendship, FriendshipResponseDTO.class))
                .toList();
    }

    @Override
    public List<FriendshipResponseDTO> getPendingRequests(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }

        List<Friendship> pendingRequests = friendshipRepository.findByPlayerBIdAndStatus(playerId, FriendshipStatus.PENDING);
        return pendingRequests.stream()
                .map(friendship -> modelMapper.map(friendship, FriendshipResponseDTO.class))
                .toList();
    }
}
