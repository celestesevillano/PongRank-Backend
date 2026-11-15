package org.example.pongrankbackend.Friendship.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PongmigosProfileDTO(
        long total,
        boolean canViewList,
        PageResponseDTO<Entry> pongmigos
) {
    public record Entry(Long id, String name, String username, boolean mutual) {}
}
