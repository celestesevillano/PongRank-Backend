package org.example.pongrankbackend.Match.service;

import lombok.Builder;
import lombok.Getter;
import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchType;
import org.example.pongrankbackend.Match.dto.MatchCreateRequestDTO;
import org.example.pongrankbackend.MatchSet.dto.MatchSetRequestDTO;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class MatchRuleValidator {

    public static final double MAX_LOCATION_DISTANCE_KM = 5.0;
    private static final double EARTH_RADIUS_KM = 6371.0;

    @Getter
    @Builder
    public static class MatchValidationResult {
        private final int winnerPlayerNumber; // 1 or 2
        private final int setsWonPlayer1;
        private final int setsWonPlayer2;
        private final String scoreSummary;
    }

    /**
     * Valida las condiciones de creación del partido según las 3 opciones: FRIEND, COMMUNITY, LOCATION.
     */
    public void validateMatchCreation(Player player1, Player player2, MatchCreateRequestDTO dto, Optional<Friendship> friendshipOpt) {
        if (player2 != null && player1.getId().equals(player2.getId())) {
            throw new InvalidMatchStateException("Un jugador no puede crear un partido contra sí mismo");
        }

        MatchType matchType = dto.getMatchType();
        if (matchType == null) {
            throw new InvalidMatchStateException("El tipo de emparejamiento (matchType) es obligatorio");
        }

        switch (matchType) {
            case FRIEND -> validateFriendMatch(player2, friendshipOpt);
            case COMMUNITY -> validateCommunityMatch(dto);
            case LOCATION -> validateLocationMatch(dto);
            case TOURNAMENT -> validateTournamentMatch(player2);
        }
    }

    private void validateTournamentMatch(Player player2) {
        if (player2 == null) {
            throw new InvalidMatchStateException("Para un partido de torneo (TOURNAMENT) es obligatorio especificar el oponente");
        }
    }

    private void validateFriendMatch(Player player2, Optional<Friendship> friendshipOpt) {
        if (player2 == null) {
            throw new InvalidMatchStateException("Para un partido entre amigos (FRIEND) es obligatorio especificar el oponente");
        }
        boolean areFriends = friendshipOpt.isPresent() && friendshipOpt.get().getStatus() == FriendshipStatus.ACCEPTED;
        if (!areFriends) {
            throw new InvalidMatchStateException("Los jugadores no tienen una relación de amistad confirmada (estado ACCEPTED)");
        }
    }

    private void validateCommunityMatch(MatchCreateRequestDTO dto) {
        if (dto.getCommunityId() == null) {
            throw new InvalidMatchStateException("Para un partido en comunidad (COMMUNITY) es obligatorio especificar el ID de la comunidad");
        }
    }

    private void validateLocationMatch(MatchCreateRequestDTO dto) {
        if (dto.getLatitude() == null || dto.getLongitude() == null) {
            throw new InvalidMatchStateException("Para un partido por cercanía (LOCATION) se requiere latitud y longitud de ubicación");
        }

        if (dto.getOpponentLatitude() != null && dto.getOpponentLongitude() != null) {
            double distance = calculateDistanceKm(
                    dto.getLatitude().doubleValue(),
                    dto.getLongitude().doubleValue(),
                    dto.getOpponentLatitude().doubleValue(),
                    dto.getOpponentLongitude().doubleValue()
            );
            if (distance > MAX_LOCATION_DISTANCE_KM) {
                throw new InvalidMatchStateException(
                        String.format("La distancia entre ambos jugadores (%.2f km) excede el radio máximo de cercanía permitido (%.1f km)",
                                distance, MAX_LOCATION_DISTANCE_KM)
                );
            }
        }
    }

    /**
     * Valida la secuencia de sets según el reglamento oficial ITTF (Reglas-de-juego.docx):
     * - Sets a 11 con diferencia de 2.
     * - Deuce a partir de 10-10 con diferencia exacta de 2.
     * - Formatos BO3 (2 sets para ganar), BO5 (3 sets para ganar), BO7 (4 sets para ganar).
     * - Corte inmediato: prohibidos sets adicionales tras alcanzar la victoria.
     * - Sin empates por set y numeración correlativa.
     */
    public MatchValidationResult validateSetScoresAndDetermineWinner(MatchFormat format, List<MatchSetRequestDTO> sets) {
        if (sets == null || sets.isEmpty()) {
            throw new InvalidMatchStateException("El reporte de marcador debe incluir al menos un set jugado");
        }

        int setsToWin = getRequiredSetsToWin(format);
        int maxSets = getMaxSetsForFormat(format);

        if (sets.size() > maxSets) {
            throw new InvalidMatchStateException(
                    String.format("La modalidad %s permite un máximo de %d sets, pero se recibieron %d",
                            format, maxSets, sets.size())
            );
        }

        // Ordenar sets por setNumber para verificar la cronología del partido
        List<MatchSetRequestDTO> sortedSets = sets.stream()
                .sorted(Comparator.comparing(MatchSetRequestDTO::getSetNumber))
                .toList();

        int winsP1 = 0;
        int winsP2 = 0;
        boolean matchDecided = false;
        int decidingSetNumber = -1;

        for (int i = 0; i < sortedSets.size(); i++) {
            MatchSetRequestDTO currentSet = sortedSets.get(i);
            int expectedSetNumber = i + 1;

            if (currentSet.getSetNumber() != expectedSetNumber) {
                throw new InvalidMatchStateException(
                        String.format("Secuencia de sets inválida: se esperaba el set número %d pero se recibió %d",
                                expectedSetNumber, currentSet.getSetNumber())
                );
            }

            // Anti-trampa de corte inmediato: Si el partido ya fue ganado en un set anterior, rechazar sets sobrantes
            if (matchDecided) {
                throw new InvalidMatchStateException(
                        String.format("El partido finalizó en el set %d al alcanzarse la victoria (%d sets ganados en modalidad %s). No se admiten sets adicionales.",
                                decidingSetNumber, setsToWin, format)
                );
            }

            validateSingleSetScore(currentSet);

            if (currentSet.getScorePlayer1() > currentSet.getScorePlayer2()) {
                winsP1++;
            } else {
                winsP2++;
            }

            if (winsP1 == setsToWin || winsP2 == setsToWin) {
                matchDecided = true;
                decidingSetNumber = expectedSetNumber;
            }
        }

        if (!matchDecided) {
            throw new InvalidMatchStateException(
                    String.format("El partido está incompleto: ningún jugador alcanzó los %d sets necesarios para ganar en formato %s (Marcador actual: %d - %d)",
                            setsToWin, format, winsP1, winsP2)
            );
        }

        int winner = (winsP1 > winsP2) ? 1 : 2;
        String summary = winsP1 + " - " + winsP2;

        return MatchValidationResult.builder()
                .winnerPlayerNumber(winner)
                .setsWonPlayer1(winsP1)
                .setsWonPlayer2(winsP2)
                .scoreSummary(summary)
                .build();
    }

    /**
     * Valida un set individual bajo las reglas oficiales:
     * - No puntajes negativos.
     * - Sin empates.
     * - Ganador con mínimo 11 puntos y mínimo 2 de diferencia.
     * - Si el perdedor tiene < 10 puntos, el ganador debe tener exactamente 11.
     * - Si el perdedor tiene >= 10 puntos (deuce), el ganador debe tener exactamente perdedor + 2.
     */
    public void validateSingleSetScore(MatchSetRequestDTO set) {
        int s1 = set.getScorePlayer1();
        int s2 = set.getScorePlayer2();

        if (s1 < 0 || s2 < 0) {
            throw new InvalidMatchStateException(
                    String.format("Set %d inválido: los puntajes no pueden ser negativos (%d-%d)", set.getSetNumber(), s1, s2)
            );
        }

        if (s1 == s2) {
            throw new InvalidMatchStateException(
                    String.format("Set %d inválido: un set no puede terminar en empate (%d-%d)", set.getSetNumber(), s1, s2)
            );
        }

        int max = Math.max(s1, s2);
        int min = Math.min(s1, s2);
        int diff = max - min;

        if (max < 11) {
            throw new InvalidMatchStateException(
                    String.format("Set %d inválido: el ganador debe alcanzar al menos 11 puntos (%d-%d)", set.getSetNumber(), s1, s2)
            );
        }

        if (diff < 2) {
            throw new InvalidMatchStateException(
                    String.format("Set %d inválido: se requiere una diferencia mínima de 2 puntos (%d-%d)", set.getSetNumber(), s1, s2)
            );
        }

        if (min < 10 && max != 11) {
            throw new InvalidMatchStateException(
                    String.format("Set %d inválido: si el rival tiene menos de 10 puntos, el ganador debió cerrar exactamente en 11 (%d-%d)",
                            set.getSetNumber(), s1, s2)
            );
        }

        if (min >= 10 && max != min + 2) {
            throw new InvalidMatchStateException(
                    String.format("Set %d inválido: en situación de ventaja (10-10 o más), el set finaliza con exactamente 2 puntos de ventaja (%d-%d)",
                            set.getSetNumber(), s1, s2)
            );
        }
    }

    public int getRequiredSetsToWin(MatchFormat format) {
        return switch (format) {
            case BO3 -> 2;
            case BO5 -> 3;
            case BO7 -> 4;
        };
    }

    public int getMaxSetsForFormat(MatchFormat format) {
        return switch (format) {
            case BO3 -> 3;
            case BO5 -> 5;
            case BO7 -> 7;
        };
    }

    /**
     * Fórmula de Haversine para cálculo de distancia en kilómetros entre dos coordenadas geográficas.
     */
    public double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
