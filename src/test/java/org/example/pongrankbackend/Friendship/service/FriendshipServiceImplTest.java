package org.example.pongrankbackend.Friendship.service;

import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.example.pongrankbackend.Friendship.dto.FriendshipRequestDTO;
import org.example.pongrankbackend.Friendship.dto.FriendshipResponseDTO;
import org.example.pongrankbackend.Friendship.repository.FriendshipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.FriendshipRequestException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.List;
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
    private ModelMapper modelMapper;

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
                .isInstanceOf(FriendshipRequestException.class)
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
        when(friendshipRepository.findFriendshipBetween(senderId, receiverId)).thenReturn(Optional.empty());
        when(friendshipRepository.save(any(Friendship.class))).thenReturn(savedFriendship);
        when(modelMapper.map(savedFriendship, FriendshipResponseDTO.class)).thenReturn(responseDto);

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
    @DisplayName("sendFriendRequest: impide crear solicitud si ya existe relación PENDING o ACCEPTED previa A->B o B->A")
    void sendFriendRequest_ExistingRelation_ThrowsException() {
        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;
        FriendshipRequestDTO dto = FriendshipRequestDTO.builder().receiverId(receiverId).build();

        Player sender = Player.builder().id(senderId).build();
        Player receiver = Player.builder().id(receiverId).build();
        Friendship existing = Friendship.builder().id(5L).playerA(sender).playerB(receiver)
                .status(FriendshipStatus.ACCEPTED).build();

        when(playerRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(friendshipRepository.findFriendshipBetween(senderId, receiverId)).thenReturn(Optional.of(existing));

        // Act & Assert
        assertThatThrownBy(() -> friendshipService.sendFriendRequest(senderId, dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya existe una relación de amistad");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendFriendRequest: una solicitud REJECTED anterior sí permite reintentar")
    void sendFriendRequest_PreviouslyRejected_AllowsRetry() {
        // Arrange
        Long senderId = 1L;
        Long receiverId = 2L;
        FriendshipRequestDTO dto = FriendshipRequestDTO.builder().receiverId(receiverId).build();

        Player sender = Player.builder().id(senderId).name("Jugador A").build();
        Player receiver = Player.builder().id(receiverId).name("Jugador B").build();
        Friendship rejected = Friendship.builder().id(5L).playerA(receiver).playerB(sender)
                .status(FriendshipStatus.REJECTED).build();

        when(playerRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(playerRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(friendshipRepository.findFriendshipBetween(senderId, receiverId)).thenReturn(Optional.of(rejected));
        when(friendshipRepository.save(rejected)).thenReturn(rejected);
        when(modelMapper.map(rejected, FriendshipResponseDTO.class))
                .thenReturn(FriendshipResponseDTO.builder().id(5L).status(FriendshipStatus.PENDING).build());

        // Act
        FriendshipResponseDTO result = friendshipService.sendFriendRequest(senderId, dto);

        // Assert
        assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(rejected.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(rejected.getPlayerA().getId()).isEqualTo(senderId);
        assertThat(rejected.getPlayerB().getId()).isEqualTo(receiverId);
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
                .isInstanceOf(ResourceNotFoundException.class)
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
                .isInstanceOf(ResourceNotFoundException.class)
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
        when(modelMapper.map(updated, FriendshipResponseDTO.class)).thenReturn(responseDto);

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
                .isInstanceOf(UnauthorizedActionException.class)
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
                .isInstanceOf(ConflictException.class)
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
                .isInstanceOf(ConflictException.class)
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
        when(modelMapper.map(updated, FriendshipResponseDTO.class)).thenReturn(responseDto);

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
                .isInstanceOf(UnauthorizedActionException.class)
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
        when(modelMapper.map(f1, FriendshipResponseDTO.class)).thenReturn(dto1);

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
        when(modelMapper.map(f1, FriendshipResponseDTO.class)).thenReturn(dto1);
        when(modelMapper.map(f2, FriendshipResponseDTO.class)).thenReturn(dto2);

        // Act
        List<FriendshipResponseDTO> result = friendshipService.getAcceptedFriends(playerId);

        // Assert
        assertThat(result).hasSize(2);
        verify(friendshipRepository).findAllByPlayerIdAndStatus(playerId, FriendshipStatus.ACCEPTED);
    }

    // ----- cancelFriendRequest -----

    @Test
    @DisplayName("cancelFriendRequest: el emisor puede cancelar su propia solicitud PENDING")
    void cancelFriendRequest_Success() {
        Long friendshipId = 10L;
        Player sender = Player.builder().id(1L).build();
        Player receiver = Player.builder().id(2L).build();
        Friendship friendship = Friendship.builder().id(friendshipId).playerA(sender).playerB(receiver)
                .status(FriendshipStatus.PENDING).build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        friendshipService.cancelFriendRequest(friendshipId, 1L);

        verify(friendshipRepository).delete(friendship);
    }

    @Test
    @DisplayName("cancelFriendRequest: solo quien envió la solicitud puede cancelarla")
    void cancelFriendRequest_NotSender_ThrowsException() {
        Long friendshipId = 10L;
        Player sender = Player.builder().id(1L).build();
        Player receiver = Player.builder().id(2L).build();
        Friendship friendship = Friendship.builder().id(friendshipId).playerA(sender).playerB(receiver)
                .status(FriendshipStatus.PENDING).build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendshipService.cancelFriendRequest(friendshipId, 2L))
                .isInstanceOf(UnauthorizedActionException.class);

        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cancelFriendRequest: no se puede cancelar una solicitud ya resuelta")
    void cancelFriendRequest_AlreadyResolved_ThrowsException() {
        Long friendshipId = 10L;
        Player sender = Player.builder().id(1L).build();
        Player receiver = Player.builder().id(2L).build();
        Friendship friendship = Friendship.builder().id(friendshipId).playerA(sender).playerB(receiver)
                .status(FriendshipStatus.ACCEPTED).build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendshipService.cancelFriendRequest(friendshipId, 1L))
                .isInstanceOf(ConflictException.class);

        verify(friendshipRepository, never()).delete(any());
    }

    // ----- unfriend -----

    @Test
    @DisplayName("unfriend: cualquiera de los dos jugadores puede terminar una amistad ACCEPTED")
    void unfriend_Success() {
        Long friendshipId = 10L;
        Player playerA = Player.builder().id(1L).build();
        Player playerB = Player.builder().id(2L).build();
        Friendship friendship = Friendship.builder().id(friendshipId).playerA(playerA).playerB(playerB)
                .status(FriendshipStatus.ACCEPTED).build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        friendshipService.unfriend(friendshipId, 2L);

        verify(friendshipRepository).delete(friendship);
    }

    @Test
    @DisplayName("unfriend: un jugador ajeno a la amistad no puede terminarla")
    void unfriend_NotParticipant_ThrowsException() {
        Long friendshipId = 10L;
        Player playerA = Player.builder().id(1L).build();
        Player playerB = Player.builder().id(2L).build();
        Friendship friendship = Friendship.builder().id(friendshipId).playerA(playerA).playerB(playerB)
                .status(FriendshipStatus.ACCEPTED).build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendshipService.unfriend(friendshipId, 99L))
                .isInstanceOf(UnauthorizedActionException.class);

        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    @DisplayName("unfriend: no se puede terminar una solicitud que no está ACCEPTED")
    void unfriend_NotAccepted_ThrowsException() {
        Long friendshipId = 10L;
        Player playerA = Player.builder().id(1L).build();
        Player playerB = Player.builder().id(2L).build();
        Friendship friendship = Friendship.builder().id(friendshipId).playerA(playerA).playerB(playerB)
                .status(FriendshipStatus.PENDING).build();

        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendshipService.unfriend(friendshipId, 1L))
                .isInstanceOf(ConflictException.class);

        verify(friendshipRepository, never()).delete(any());
    }
}
