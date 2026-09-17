package org.example.pongrankbackend.Friendship.service;

import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.example.pongrankbackend.Friendship.dto.FriendshipRequestDTO;
import org.example.pongrankbackend.Friendship.dto.FriendshipResponseDTO;
import org.example.pongrankbackend.Friendship.mapper.FriendshipMapper;
import org.example.pongrankbackend.Friendship.repository.FriendshipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceImplTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private FriendshipMapper friendshipMapper;

    @InjectMocks
    private FriendshipServiceImpl friendshipService;

    @Test
    @DisplayName("sendFriendRequest: no permite enviarse solicitud a sí mismo")
    void sendFriendRequest_SelfRequest_ThrowsException() {
        // Arrange
        Long playerId = 1L;
        FriendshipRequestDTO dto = FriendshipRequestDTO.builder().receiverId(playerId).build();

        // Act & Assert
        assertThatThrownBy(() -> friendshipService.sendFriendRequest(playerId, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a sí mismo");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendFriendRequest: crea solicitud en estado PENDING correctamente")
    void sendFriendRequest_Success() {
        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;
        FriendshipRequestDTO dto = FriendshipRequestDTO.builder().receiverId(receiverId).build();

        Player sender = Player.builder().id(senderId).name("Jugador A").build();
        Player receiver = Player.builder().id(receiverId).name("Jugador B").build();

        Friendship savedFriendship = Friendship.builder()
                .id(10L)
                .playerA(sender)
                .playerB(receiver)
                .status(FriendshipStatus.PENDING)
                .build();

        FriendshipResponseDTO responseDto = FriendshipResponseDTO.builder()
                .id(10L)
                .playerA(PlayerSummaryDTO.builder().id(senderId).name("Jugador A").build())
                .playerB(PlayerSummaryDTO.builder().id(receiverId).name("Jugador B").build())
                .status(FriendshipStatus.PENDING)
                .build();

        when(playerRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(friendshipRepository.existsFriendshipBetween(senderId, receiverId)).thenReturn(false);
        when(friendshipRepository.save(any(Friendship.class))).thenReturn(savedFriendship);
        when(friendshipMapper.toDto(savedFriendship)).thenReturn(responseDto);

        // Act
        FriendshipResponseDTO result = friendshipService.sendFriendRequest(senderId, dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);

        ArgumentCaptor<Friendship> captor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(captor.capture());
        Friendship created = captor.getValue();

        assertThat(created.getPlayerA().getId()).isEqualTo(senderId);
        assertThat(created.getPlayerB().getId()).isEqualTo(receiverId);
        assertThat(created.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    @DisplayName("sendFriendRequest: impide crear solicitud si ya existe relación previa A->B o B->A")
    void sendFriendRequest_ExistingRelation_ThrowsException() {
        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;
        FriendshipRequestDTO dto = FriendshipRequestDTO.builder().receiverId(receiverId).build();

        Player sender = Player.builder().id(senderId).build();
        Player receiver = Player.builder().id(receiverId).build();

        when(playerRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(friendshipRepository.existsFriendshipBetween(senderId, receiverId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> friendshipService.sendFriendRequest(senderId, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ya existe una relación de amistad");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendFriendRequest: lanza excepción cuando el emisor no existe")
    void sendFriendRequest_SenderNotFound_ThrowsException() {
        // Arrange
        Long senderId = 99L;
        Long receiverId = 2L;
        FriendshipRequestDTO dto = FriendshipRequestDTO.builder().receiverId(receiverId).build();

        when(playerRepository.findById(senderId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> friendshipService.sendFriendRequest(senderId, dto))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Jugador emisor no encontrado");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendFriendRequest: lanza excepción cuando el receptor no existe")
    void sendFriendRequest_ReceiverNotFound_ThrowsException() {
        // Arrange
        Long senderId = 1L;
        Long receiverId = 99L;
        FriendshipRequestDTO dto = FriendshipRequestDTO.builder().receiverId(receiverId).build();

        Player sender = Player.builder().id(senderId).build();
        when(playerRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(receiverId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> friendshipService.sendFriendRequest(senderId, dto))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Jugador receptor no encontrado");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("acceptFriendRequest: cambia de PENDING a ACCEPTED cuando lo ejecuta playerB")
    void acceptFriendRequest_Success() {
        // Arrange
        Long friendshipId = 10L;
        Long playerBId = 2L;

        Player playerA = Player.builder().id(1L).build();
        Player playerB = Player.builder().id(playerBId).build();

        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .playerA(playerA)
                .playerB(playerB)
                .status(FriendshipStatus.PENDING)
                .build();

        Friendship updated = Friendship.builder()
                .id(friendshipId)
                .playerA(playerA)
                .playerB(playerB)
                .status(FriendshipStatus.ACCEPTED)
                .build();

        FriendshipResponseDTO responseDto = FriendshipResponseDTO.builder()
                .id(friendshipId)
                .status(FriendshipStatus.ACCEPTED)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(friendship)).thenReturn(updated);
        when(friendshipMapper.toDto(updated)).thenReturn(responseDto);

        // Act
        FriendshipResponseDTO result = friendshipService.acceptFriendRequest(friendshipId, playerBId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        verify(friendshipRepository).save(friendship);
    }

    @Test
    @DisplayName("acceptFriendRequest: no permite que playerA (emisor) acepte la solicitud")
    void acceptFriendRequest_OnlyPlayerB_CanAccept() {
        // Arrange
        Long friendshipId = 10L;
        Long playerAId = 1L; // Emisor intentando aceptar
        Long playerBId = 2L;

        Player playerA = Player.builder().id(playerAId).build();
        Player playerB = Player.builder().id(playerBId).build();

        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .playerA(playerA)
                .playerB(playerB)
                .status(FriendshipStatus.PENDING)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        // Act & Assert
        assertThatThrownBy(() -> friendshipService.acceptFriendRequest(friendshipId, playerAId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el receptor original puede aceptar");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("acceptFriendRequest: lanza excepción si la amistad ya fue ACCEPTED")
    void acceptFriendRequest_AlreadyAccepted_ThrowsException() {
        // Arrange
        Long friendshipId = 10L;
        Long playerBId = 2L;

        Player playerA = Player.builder().id(1L).build();
        Player playerB = Player.builder().id(playerBId).build();

        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .playerA(playerA)
                .playerB(playerB)
                .status(FriendshipStatus.ACCEPTED)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        // Act & Assert
        assertThatThrownBy(() -> friendshipService.acceptFriendRequest(friendshipId, playerBId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no está en estado PENDING");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("acceptFriendRequest: lanza excepción si la amistad está en estado REJECTED")
    void acceptFriendRequest_AlreadyRejected_ThrowsException() {
        // Arrange
        Long friendshipId = 10L;
        Long playerBId = 2L;

        Player playerA = Player.builder().id(1L).build();
        Player playerB = Player.builder().id(playerBId).build();

        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .playerA(playerA)
                .playerB(playerB)
                .status(FriendshipStatus.REJECTED)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        // Act & Assert
        assertThatThrownBy(() -> friendshipService.acceptFriendRequest(friendshipId, playerBId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no está en estado PENDING");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejectFriendRequest: cambia de PENDING a REJECTED cuando lo ejecuta playerB")
    void rejectFriendRequest_Success() {
        // Arrange
        Long friendshipId = 10L;
        Long playerBId = 2L;

        Player playerA = Player.builder().id(1L).build();
        Player playerB = Player.builder().id(playerBId).build();

        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .playerA(playerA)
                .playerB(playerB)
                .status(FriendshipStatus.PENDING)
                .build();

        Friendship updated = Friendship.builder()
                .id(friendshipId)
                .playerA(playerA)
                .playerB(playerB)
                .status(FriendshipStatus.REJECTED)
                .build();

        FriendshipResponseDTO responseDto = FriendshipResponseDTO.builder()
                .id(friendshipId)
                .status(FriendshipStatus.REJECTED)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(friendship)).thenReturn(updated);
        when(friendshipMapper.toDto(updated)).thenReturn(responseDto);

        // Act
        FriendshipResponseDTO result = friendshipService.rejectFriendRequest(friendshipId, playerBId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(FriendshipStatus.REJECTED);
        verify(friendshipRepository).save(friendship);
    }

    @Test
    @DisplayName("rejectFriendRequest: no permite que playerA rechace la solicitud")
    void rejectFriendRequest_OnlyPlayerB_CanReject() {
        // Arrange
        Long friendshipId = 10L;
        Long playerAId = 1L;
        Long playerBId = 2L;

        Player playerA = Player.builder().id(playerAId).build();
        Player playerB = Player.builder().id(playerBId).build();

        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .playerA(playerA)
                .playerB(playerB)
                .status(FriendshipStatus.PENDING)
                .build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        // Act & Assert
        assertThatThrownBy(() -> friendshipService.rejectFriendRequest(friendshipId, playerAId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el receptor original puede rechazar");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPendingRequests: devuelve solicitudes recibidas por el jugador en estado PENDING")
    void getPendingRequests_Success() {
        // Arrange
        Long playerId = 2L;
        Friendship f1 = Friendship.builder().id(100L).status(FriendshipStatus.PENDING).build();
        FriendshipResponseDTO dto1 = FriendshipResponseDTO.builder().id(100L).status(FriendshipStatus.PENDING).build();

        when(playerRepository.existsById(playerId)).thenReturn(true);
        when(friendshipRepository.findByPlayerBIdAndStatus(playerId, FriendshipStatus.PENDING)).thenReturn(List.of(f1));
        when(friendshipMapper.toDto(f1)).thenReturn(dto1);

        // Act
        List<FriendshipResponseDTO> result = friendshipService.getPendingRequests(playerId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(100L);
        verify(friendshipRepository).findByPlayerBIdAndStatus(playerId, FriendshipStatus.PENDING);
    }

    @Test
    @DisplayName("getAcceptedFriends: devuelve amistades ACCEPTED donde el jugador participa como playerA o playerB")
    void getAcceptedFriends_Success() {
        // Arrange
        Long playerId = 1L;
        Friendship f1 = Friendship.builder().id(100L).status(FriendshipStatus.ACCEPTED).build();
        Friendship f2 = Friendship.builder().id(200L).status(FriendshipStatus.ACCEPTED).build();

        FriendshipResponseDTO dto1 = FriendshipResponseDTO.builder().id(100L).status(FriendshipStatus.ACCEPTED).build();
        FriendshipResponseDTO dto2 = FriendshipResponseDTO.builder().id(200L).status(FriendshipStatus.ACCEPTED).build();

        when(playerRepository.existsById(playerId)).thenReturn(true);
        when(friendshipRepository.findAllByPlayerIdAndStatus(playerId, FriendshipStatus.ACCEPTED)).thenReturn(List.of(f1, f2));
        when(friendshipMapper.toDto(f1)).thenReturn(dto1);
        when(friendshipMapper.toDto(f2)).thenReturn(dto2);

        // Act
        List<FriendshipResponseDTO> result = friendshipService.getAcceptedFriends(playerId);

        // Assert
        assertThat(result).hasSize(2);
        verify(friendshipRepository).findAllByPlayerIdAndStatus(playerId, FriendshipStatus.ACCEPTED);
    }
}
