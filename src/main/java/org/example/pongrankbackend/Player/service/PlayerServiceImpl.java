package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.EmailAlreadyExistsException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlayerServiceImpl implements PlayerService {

    private final PlayerRepository playerRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;

    public PlayerServiceImpl(PlayerRepository playerRepository,
                             ModelMapper modelMapper,
                             PasswordEncoder passwordEncoder) {
        this.playerRepository = playerRepository;
        this.modelMapper = modelMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public PlayerResponseDTO registerPlayer(PlayerRegisterRequestDTO dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        if (playerRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("El email '" + normalizedEmail + "' ya se encuentra registrado");
        }

        Player player = modelMapper.map(dto, Player.class);
        player.setEmail(normalizedEmail);
        player.setPassword(passwordEncoder.encode(dto.getPassword()));
        player.setRole(Role.ROLE_USER);
        player.setStatus(PlayerStatus.ACTIVE);

        if (player.getShareContact() == null) {
            player.setShareContact(false);
        }
        if (player.getFederatedDeclared() == null) {
            player.setFederatedDeclared(false);
        }

        Player savedPlayer = playerRepository.save(player);
        return modelMapper.map(savedPlayer, PlayerResponseDTO.class);
    }

    @Override
    public PlayerResponseDTO getPlayerById(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + id));
        PlayerResponseDTO response = modelMapper.map(player, PlayerResponseDTO.class);
        Long currentUserId = org.example.pongrankbackend.security.SecurityUtils.getCurrentUserId().orElse(null);
        boolean isOwnerOrAdmin = (currentUserId != null && currentUserId.equals(id))
                || org.example.pongrankbackend.security.SecurityUtils.hasRole("SYSTEM_ADMIN");
        if (!isOwnerOrAdmin && Boolean.FALSE.equals(player.getShareContact())) {
            response.setEmail(null);
            response.setWhatsapp(null);
        }
        return response;
    }

    @Override
    public PlayerSummaryDTO getPlayerSummaryById(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + id));
        return modelMapper.map(player, PlayerSummaryDTO.class);
    }

    @Override
    @Transactional
    public PlayerResponseDTO updatePlayer(Long playerId, PlayerUpdateRequestDTO dto) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId));

        modelMapper.map(dto, player);

        Player updatedPlayer = playerRepository.save(player);
        return modelMapper.map(updatedPlayer, PlayerResponseDTO.class);
    }
}
