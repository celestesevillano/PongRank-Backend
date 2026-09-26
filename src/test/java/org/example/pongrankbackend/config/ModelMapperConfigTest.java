package org.example.pongrankbackend.config;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ModelMapperConfigTest {

    private ModelMapper modelMapper;

    @BeforeEach
    void setUp() {
        modelMapper = new ModelMapperConfig().modelMapper();
    }

    private Player player(Boolean shareContact) {
        return Player.builder()
                .id(1L)
                .name("Adriana")
                .whatsapp("+51999999999")
                .shareContact(shareContact)
                .build();
    }

    @Test
    @DisplayName("Player -> PlayerSummaryDTO: incluye el WhatsApp cuando shareContact=true")
    void mapToPlayerSummaryDTO_ShareContactTrue_IncludesWhatsapp() {
        PlayerSummaryDTO dto = modelMapper.map(player(true), PlayerSummaryDTO.class);

        assertThat(dto.getWhatsapp()).isEqualTo("+51999999999");
    }

    @Test
    @DisplayName("Player -> PlayerSummaryDTO: oculta el WhatsApp cuando shareContact=false")
    void mapToPlayerSummaryDTO_ShareContactFalse_HidesWhatsapp() {
        PlayerSummaryDTO dto = modelMapper.map(player(false), PlayerSummaryDTO.class);

        assertThat(dto.getWhatsapp()).isNull();
    }

    @Test
    @DisplayName("Player -> PlayerSummaryDTO: oculta el WhatsApp cuando shareContact es null")
    void mapToPlayerSummaryDTO_ShareContactNull_HidesWhatsapp() {
        PlayerSummaryDTO dto = modelMapper.map(player(null), PlayerSummaryDTO.class);

        assertThat(dto.getWhatsapp()).isNull();
    }
}
