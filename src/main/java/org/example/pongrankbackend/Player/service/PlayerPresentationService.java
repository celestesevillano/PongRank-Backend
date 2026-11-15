package org.example.pongrankbackend.Player.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerPresentation;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.dto.PlayerPresentationDTO;
import org.example.pongrankbackend.Player.repository.PlayerPresentationRepository;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.InvalidProfileException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlayerPresentationService {

    private static final String JPEG_PREFIX = "data:image/jpeg;base64,";
    private static final int MAX_DIMENSION = 512;
    private final PlayerRepository players;
    private final PlayerPresentationRepository presentations;

    public PlayerPresentationService(PlayerRepository players, PlayerPresentationRepository presentations) {
        this.players = players;
        this.presentations = presentations;
    }

    public PlayerPresentationDTO find(Long id) {
        Player player = players.findById(id).filter(PlayerPresentationService::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado"));
        return toDto(presentations.findById(id).orElseGet(() -> new PlayerPresentation(player)), player.getName());
    }

    @Transactional
    public PlayerPresentationDTO updateOwn(PlayerPresentationDTO dto) {
        Long id = SecurityUtils.getRequiredCurrentUserId();
        String city = clean(dto.city());
        String description = clean(dto.description());
        String photo = validatePhoto(dto.photo());
        String instagram = validateLink(dto.instagram(), "instagram.com", "Instagram");
        String tiktok = validateLink(dto.tiktok(), "tiktok.com", "TikTok");
        String facebook = validateLink(dto.facebook(), "facebook.com", "Facebook");

        Player player = players.lockForUpdate(id).filter(PlayerPresentationService::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado"));
        PlayerPresentation presentation = presentations.findById(id).orElseGet(() -> new PlayerPresentation(player));
        player.setName(dto.name().strip());
        presentation.setCity(city);
        presentation.setDescription(description);
        presentation.setPhoto(photo);
        presentation.setInstagram(instagram);
        presentation.setTiktok(tiktok);
        presentation.setFacebook(facebook);

        return toDto(presentations.save(presentation), player.getName());
    }

    private static boolean isActive(Player player) {
        return player.getStatus() == PlayerStatus.ACTIVE;
    }

    private static PlayerPresentationDTO toDto(PlayerPresentation presentation, String name) {
        return new PlayerPresentationDTO(name, presentation.getCity(), presentation.getDescription(), presentation.getPhoto(),
                presentation.getInstagram(), presentation.getTiktok(), presentation.getFacebook());
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    static String validateLink(String raw, String domain, String label) {
        String value = clean(raw);
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                    && (host.equals(domain) || host.endsWith("." + domain))
                    && uri.getPath() != null && uri.getPath().length() > 1) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Return the same validation error for malformed and disallowed links.
        }
        throw new InvalidProfileException("Revisa el enlace de " + label + ".");
    }

    static String validatePhoto(String raw) {
        String value = clean(raw);
        if (value == null) return null;
        InvalidProfileException invalid = new InvalidProfileException("Usa una foto JPEG válida de hasta 512 píxeles por lado.");
        if (!value.startsWith(JPEG_PREFIX) || value.length() > 240000) throw invalid;
        try {
            byte[] bytes = Base64.getDecoder().decode(value.substring(JPEG_PREFIX.length()));
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid;
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    if (!"JPEG".equals(reader.getFormatName()) || reader.getWidth(0) > MAX_DIMENSION
                            || reader.getHeight(0) > MAX_DIMENSION) throw invalid;
                    var cleanImage = new ByteArrayOutputStream();
                    ImageIO.write(reader.read(0), "JPEG", cleanImage);
                    String sanitized = JPEG_PREFIX + Base64.getEncoder().encodeToString(cleanImage.toByteArray());
                    if (sanitized.length() > 240000) throw invalid;
                    return sanitized;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException error) {
            throw invalid;
        }
    }
}
