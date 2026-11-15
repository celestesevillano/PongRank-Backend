package org.example.pongrankbackend.Friendship.service;

import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.example.pongrankbackend.Friendship.dto.FriendshipRequestDTO;
import org.example.pongrankbackend.Friendship.dto.FriendshipResponseDTO;
import org.example.pongrankbackend.Friendship.dto.PongmigosProfileDTO;
import org.example.pongrankbackend.Friendship.dto.PongmigosVisibilityDTO;
import org.example.pongrankbackend.Friendship.repository.FriendshipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.PongmigosVisibility;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.security.PlayerAccessGuard;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.FriendshipRequestException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final PlayerRepository playerRepository;
    private final ModelMapper modelMapper;
    private final PlayerAccessGuard playerAccessGuard;

    public FriendshipServiceImpl(FriendshipRepository friendshipRepository,
                                  PlayerRepository playerRepository,
                                  ModelMapper modelMapper,
                                  PlayerAccessGuard playerAccessGuard) {
        this.friendshipRepository = friendshipRepository;
        this.playerRepository = playerRepository;
        this.modelMapper = modelMapper;
        this.playerAccessGuard = playerAccessGuard;
    }

    @Override
    @Transactional
    public FriendshipResponseDTO sendFriendRequest(Long requestedSenderId, Long actingPlayerId, FriendshipRequestDTO dto) {
        if (requestedSenderId != null) {
            playerAccessGuard.requireSelfOrAdmin(requestedSenderId,
                    "No puedes enviar solicitudes de amistad en nombre de otro jugador");
        }
        return sendFriendRequest(actingPlayerId, dto);
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

        var existing = friendshipRepository.findFriendshipBetween(senderId, receiverId);
        if (existing.isPresent()) {
            Friendship friendship = existing.get();

            // Un rechazo anterior no bloquea para siempre: se puede volver a intentar
            if (friendship.getStatus() != FriendshipStatus.REJECTED) {
                throw new ConflictException("Ya existe una relación de amistad o solicitud pendiente entre ambos jugadores");
            }

            friendship.setPlayerA(sender);
            friendship.setPlayerB(receiver);
            friendship.setStatus(FriendshipStatus.PENDING);
            Friendship updatedFriendship = friendshipRepository.save(friendship);
            return modelMapper.map(updatedFriendship, FriendshipResponseDTO.class);
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
    public void cancelFriendRequest(Long friendshipId, Long actingPlayerId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de amistad no encontrada con ID: " + friendshipId));

        if (!friendship.getPlayerA().getId().equals(actingPlayerId)) {
            throw new UnauthorizedActionException("Solo quien envió la solicitud puede cancelarla");
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ConflictException("La solicitud de amistad ya fue procesada o no está en estado PENDING");
        }

        friendshipRepository.delete(friendship);
    }

    @Override
    @Transactional
    public void unfriend(Long friendshipId, Long actingPlayerId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de amistad no encontrada con ID: " + friendshipId));

        boolean isParticipant = friendship.getPlayerA().getId().equals(actingPlayerId)
                || friendship.getPlayerB().getId().equals(actingPlayerId);
        if (!isParticipant) {
            throw new UnauthorizedActionException("Solo los jugadores de la amistad pueden terminarla");
        }

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new ConflictException("Solo se puede terminar una amistad ya aceptada");
        }

        friendshipRepository.delete(friendship);
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
        playerAccessGuard.requireSelfOrAdmin(playerId, "Solo puedes consultar tu propia lista de amigos");
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
        playerAccessGuard.requireSelfOrAdmin(playerId,
                "Solo puedes consultar tus propias solicitudes de amistad pendientes");
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }

        List<Friendship> pendingRequests = friendshipRepository.findByPlayerBIdAndStatus(playerId, FriendshipStatus.PENDING);
        return pendingRequests.stream()
                .map(friendship -> modelMapper.map(friendship, FriendshipResponseDTO.class))
                .toList();
    }

    @Override
    public List<FriendshipResponseDTO> getSentRequests(Long playerId) {
        playerAccessGuard.requireSelfOrAdmin(playerId,
                "Solo puedes consultar tus propias solicitudes de amistad enviadas");
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }

        List<Friendship> sentRequests = friendshipRepository.findByPlayerAIdAndStatus(playerId, FriendshipStatus.PENDING);
        return sentRequests.stream()
                .map(friendship -> modelMapper.map(friendship, FriendshipResponseDTO.class))
                .toList();
    }

    @Override
    public PongmigosVisibilityDTO getPongmigosVisibility(Long playerId) {
        return new PongmigosVisibilityDTO(visibilityOf(findActivePlayer(playerId)));
    }

    @Override
    @Transactional
    public PongmigosVisibilityDTO updatePongmigosVisibility(Long playerId, PongmigosVisibility visibility) {
        Player player = findActivePlayer(playerId);
        player.setPongmigosVisibility(visibility);
        return new PongmigosVisibilityDTO(visibility);
    }

    @Override
    public PongmigosProfileDTO getPongmigosProfile(Long playerId, Long viewerId, int page, int size) {
        Player player = findActivePlayer(playerId);
        boolean viewingOwnProfile = playerId.equals(viewerId);
        if (!viewingOwnProfile && visibilityOf(player) == PongmigosVisibility.ONLY_ME) {
            return new PongmigosProfileDTO(friendshipRepository.countActivePongmigos(playerId), false, null);
        }

        Page<Player> pongmigos = friendshipRepository.listActivePongmigos(playerId,
                PageRequestFactory.of(page, size, Sort.by("name", "id")));
        Set<Long> mutualIds = viewingOwnProfile || pongmigos.isEmpty() ? Set.of()
                : friendshipRepository.findActivePongmigoIdsAmong(viewerId,
                        pongmigos.getContent().stream().map(Player::getId).toList());

        return new PongmigosProfileDTO(pongmigos.getTotalElements(), true,
                PageResponseDTO.from(pongmigos, friend -> new PongmigosProfileDTO.Entry(
                        friend.getId(), friend.getName(), friend.getUsername(), mutualIds.contains(friend.getId()))));
    }

    private Player findActivePlayer(Long playerId) {
        return playerRepository.findById(playerId)
                .filter(player -> player.getStatus() == PlayerStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId));
    }

    private PongmigosVisibility visibilityOf(Player player) {
        // Las cuentas anteriores a este campo tienen la lista privada hasta que el titular la haga visible.
        return player.getPongmigosVisibility() == PongmigosVisibility.EVERYONE
                ? PongmigosVisibility.EVERYONE : PongmigosVisibility.ONLY_ME;
    }
}
