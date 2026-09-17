package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;
import org.example.pongrankbackend.Player.mapper.PlayerMapper;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class PlayerServiceImpl implements PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final PasswordEncoder passwordEncoder;

    public PlayerServiceImpl(PlayerRepository playerRepository,
                             PlayerMapper playerMapper,
                             PasswordEncoder passwordEncoder) {
        this.playerRepository = playerRepository;
        this.playerMapper = playerMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public PlayerResponseDTO registerPlayer(PlayerRegisterRequestDTO dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        if (playerRepository.existsByEmail(normalizedEmail)) {
            // TODO: Replace with custom EmailAlreadyExistsException
            throw new IllegalArgumentException("El email '" + normalizedEmail + "' ya se encuentra registrado");
        }

        Player player = playerMapper.toEntity(dto);
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
        return playerMapper.toDto(savedPlayer);
    }

    @Override
    public PlayerResponseDTO getPlayerById(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado con ID: " + id)); // TODO: Replace with custom ResourceNotFoundException
        return playerMapper.toDto(player);
    }

    @Override
    public PlayerSummaryDTO getPlayerSummaryById(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado con ID: " + id)); // TODO: Replace with custom ResourceNotFoundException
        return playerMapper.toSummaryDto(player);
    }

    @Override
    @Transactional
    public PlayerResponseDTO updatePlayer(Long playerId, PlayerUpdateRequestDTO dto) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado con ID: " + playerId)); // TODO: Replace with custom ResourceNotFoundException

        playerMapper.updateEntityFromDto(dto, player);

        Player updatedPlayer = playerRepository.save(player);
        return playerMapper.toDto(updatedPlayer);
    }
}
