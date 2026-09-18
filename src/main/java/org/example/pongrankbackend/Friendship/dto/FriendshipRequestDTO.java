package org.example.pongrankbackend.Friendship.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendshipRequestDTO {

    @NotNull(message = "El ID del jugador destinatario es obligatorio")
    private Long receiverId;
}
