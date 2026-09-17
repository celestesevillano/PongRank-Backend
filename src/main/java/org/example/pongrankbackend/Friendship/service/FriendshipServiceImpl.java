package org.example.pongrankbackend.Friendship.service;

import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.example.pongrankbackend.Friendship.dto.FriendshipRequestDTO;
import org.example.pongrankbackend.Friendship.dto.FriendshipResponseDTO;
import org.example.pongrankbackend.Friendship.mapper.FriendshipMapper;
import org.example.pongrankbackend.Friendship.repository.FriendshipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final PlayerRepository playerRepository;
    private final FriendshipMapper friendshipMapper;

    public FriendshipServiceImpl(FriendshipRepository friendshipRepository,
                                  PlayerRepository playerRepository,
                                  FriendshipMapper friendshipMapper) {
        this.friendshipRepository = friendshipRepository;
        this.playerRepository = playerRepository;
        this.friendshipMapper = friendshipMapper;
    }

    @Override
    @Transactional
    public FriendshipResponseDTO sendFriendRequest(Long senderId, FriendshipRequestDTO dto) {
        Long receiverId = dto.getReceiverId();

        if (senderId.equals(receiverId)) {
            // TODO: Replace with custom BadRequestException
            throw new IllegalArgumentException("Un jugador no puede enviarse una solicitud de amistad a sí mismo");
        }

        Player sender = playerRepository.findById(senderId)
                .orElseThrow(() -> new NoSuchElementException("Jugador emisor no encontrado con ID: " + senderId)); // TODO: Replace with custom ResourceNotFoundException

        Player receiver = playerRepository.findById(receiverId)
                .orElseThrow(() -> new NoSuchElementException("Jugador receptor no encontrado con ID: " + receiverId)); // TODO: Replace with custom ResourceNotFoundException

        if (friendshipRepository.existsFriendshipBetween(senderId, receiverId)) {
            // TODO: Replace with custom DuplicateResourceException / ConflictException
            throw new IllegalStateException("Ya existe una relación de amistad o solicitud pendiente entre ambos jugadores");
        }

        Friendship friendship = Friendship.builder()
                .playerA(sender)
                .playerB(receiver)
                .status(FriendshipStatus.PENDING)
                .build();

        Friendship savedFriendship = friendshipRepository.save(friendship);
        return friendshipMapper.toDto(savedFriendship);
    }

    @Override
    @Transactional
    public FriendshipResponseDTO acceptFriendRequest(Long friendshipId, Long actingPlayerId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new NoSuchElementException("Solicitud de amistad no encontrada con ID: " + friendshipId)); // TODO: Replace with custom ResourceNotFoundException

        if (!friendship.getPlayerB().getId().equals(actingPlayerId)) {
            // TODO: Replace with custom ForbiddenException / AccessDeniedException
            throw new IllegalArgumentException("Solo el receptor original puede aceptar la solicitud de amistad");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            // TODO: Replace with custom IllegalStateException
            throw new IllegalStateException("La solicitud de amistad ya fue procesada o no está en estado PENDING");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        Friendship updatedFriendship = friendshipRepository.save(friendship);
        return friendshipMapper.toDto(updatedFriendship);
    }

    @Override
    @Transactional
    public FriendshipResponseDTO rejectFriendRequest(Long friendshipId, Long actingPlayerId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new NoSuchElementException("Solicitud de amistad no encontrada con ID: " + friendshipId)); // TODO: Replace with custom ResourceNotFoundException

        if (!friendship.getPlayerB().getId().equals(actingPlayerId)) {
            // TODO: Replace with custom ForbiddenException / AccessDeniedException
            throw new IllegalArgumentException("Solo el receptor original puede rechazar la solicitud de amistad");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            // TODO: Replace with custom IllegalStateException
            throw new IllegalStateException("La solicitud de amistad ya fue procesada o no está en estado PENDING");
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        Friendship updatedFriendship = friendshipRepository.save(friendship);
        return friendshipMapper.toDto(updatedFriendship);
    }

    @Override
    public List<FriendshipResponseDTO> getAcceptedFriends(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            // TODO: Replace with custom ResourceNotFoundException
            throw new NoSuchElementException("Jugador no encontrado con ID: " + playerId);
        }

        List<Friendship> friendships = friendshipRepository.findAllByPlayerIdAndStatus(playerId, FriendshipStatus.ACCEPTED);
        return friendships.stream()
                .map(friendshipMapper::toDto)
                .toList();
    }

    @Override
    public List<FriendshipResponseDTO> getPendingRequests(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            // TODO: Replace with custom ResourceNotFoundException
            throw new NoSuchElementException("Jugador no encontrado con ID: " + playerId);
        }

        List<Friendship> pendingRequests = friendshipRepository.findByPlayerBIdAndStatus(playerId, FriendshipStatus.PENDING);
        return pendingRequests.stream()
                .map(friendshipMapper::toDto)
                .toList();
    }
}
