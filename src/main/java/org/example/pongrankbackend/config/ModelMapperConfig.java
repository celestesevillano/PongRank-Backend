package org.example.pongrankbackend.config;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.modelmapper.Conditions;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STANDARD)
                .setPropertyCondition(Conditions.isNotNull());

        // PlayerSummaryDTO se usa en Match, Tournament, Club, Friendship, etc. — el WhatsApp
        // solo debe viajar ahí si el jugador activó shareContact, sin importar desde dónde se mapee.
        modelMapper.createTypeMap(Player.class, PlayerSummaryDTO.class)
                .setPostConverter(context -> {
                    PlayerSummaryDTO destination = context.getDestination();
                    Player source = context.getSource();
                    if (source.getShareContact() == null || !source.getShareContact()) {
                        destination.setWhatsapp(null);
                    }
                    return destination;
                });

        return modelMapper;
    }
}
