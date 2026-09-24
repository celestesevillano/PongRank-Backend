# Club, ClubMembership y Tournament — guía de verificación

Responsable: Emiliano (integrante 4). Rama: `feature/club-tournament-emiliano`.

> `actingPlayerId` es PROVISIONAL: lo envía el cliente y **no es autenticación**. Debe reemplazarse por la
> identidad del JWT (buscar `TODO: Replace actingPlayerId`).

Errores (excepciones compartidas de `common.exception`): recurso inexistente **404**
(`ResourceNotFoundException`), conflicto de negocio **409** (`ConflictException`), actor sin permiso **403**
(`UnauthorizedActionException`), validación **400**. Un 500 indica un defecto.

## 1. Pruebas unitarias (sin base de datos)

```bash
cd ~/Documents/UTEC/2026-2/dbp/PongRank-Backend
./mvnw test
```
Esperado: `BUILD SUCCESS` y `Tests run: N, Failures: 0, Errors: 0`.
(`PongRankBackendApplicationTests` está `@Disabled` por el equipo, así que no necesita PostgreSQL.)

## 2. Prueba de integración HTTP contra una base AISLADA (no toca tus datos)

```bash
# Terminal 1: PostgreSQL del proyecto y base aislada (CREATE DATABASE no borra nada)
docker compose up -d postgres
docker exec pongrank-postgres psql -U postgres -c "CREATE DATABASE pongrank_it"

# Terminal 1: levantar la app contra la base aislada en el puerto 8081
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5435/pongrank_it PORT=8081 ./mvnw spring-boot:run

# Terminal 2: ejecutar el escenario completo
./docs/club-tournament/integration-test.sh http://localhost:8081
```
Esperado al final: `RESULT: N passed, 0 failed`.
El puerto 5435 viene de tu `docker-compose.override.yml`. Cualquier 500 cuenta como fallo.

El cuarto parámetro elige cómo se juegan los partidos:

| Modo | Qué hace | Cuándo usarlo |
|---|---|---|
| `http` (por defecto) | Los jugadores usan `submit` + `confirm` de Match y la llave avanza sola con `MatchConfirmedEvent` | Prueba de extremo a extremo |
| `sql` | Escribe en `pongrank_it` un resultado CONFIRMED con sus sets y llama a `sync-results`, que lo lee con `MatchService.getMatchById` | Verificar Tournament mientras `submitScore` tenga el defecto de la sección 7 |

```bash
./docs/club-tournament/integration-test.sh http://localhost:8081 pongrank-postgres pongrank_it sql
```

## 3. Postman
Importar `PongRank-Club-Tournament.postman_collection.json` y ajustar las variables
(`systemAdminId`, `clubAdminId`, `playerId`, `clubId`...). Las peticiones de creación guardan los ids solas.

## 4. Endpoints

| Módulo | Método y ruta | Actor |
|---|---|---|
| Club | `POST /api/v1/clubs` | jugador (será admin) |
| | `GET /api/v1/clubs`, `GET /api/v1/clubs/{id}` | público |
| | `PATCH /api/v1/clubs/{id}` | admin del club |
| | `GET /api/v1/clubs/{id}/review` | admin del club o admin general |
| | `PATCH /api/v1/clubs/{id}/resubmit` | admin del club (REJECTED → PENDING) |
| | `PATCH /api/v1/clubs/{id}/affiliation-document` | admin del club (APPROVED/PENDING → PENDING) |
| | `PATCH /api/v1/clubs/{id}/admin` | admin del club (transferencia) |
| Admin general | `GET /api/v1/admin/clubs/pending` | admin general |
| | `PATCH /api/v1/admin/clubs/{id}/approve` · `/reject` | admin general |
| | `GET /api/v1/admin/clubs/{id}/reviews` | admin general |
| ClubMembership | `POST /api/v1/club-memberships` | jugador |
| | `GET /api/v1/club-memberships/clubs/{clubId}/pending` | admin del club |
| | `GET /api/v1/club-memberships/clubs/{clubId}/members` | público |
| | `GET /api/v1/club-memberships/players/{playerId}` | público |
| | `PATCH /api/v1/club-memberships/{id}/approve` · `/reject` | admin del club |
| | `PATCH /api/v1/club-memberships/{id}/cancel` · `/leave` | el propio jugador |
| Tournament | `POST /api/v1/tournaments` | admin del club |
| | `GET /api/v1/tournaments/{id}`, `GET /api/v1/tournaments/clubs/{clubId}` | público |
| | `GET/POST /api/v1/tournaments/{id}/participants`, `DELETE .../participants/{playerId}` | admin (POST/DELETE) |
| | `PUT /api/v1/tournaments/{id}/seeding` | admin |
| | `POST /api/v1/tournaments/{id}/start` | admin |
| | `GET /api/v1/tournaments/{id}/matches`, `GET /api/v1/tournaments/{id}/groups` | público |
| | `PUT /api/v1/tournaments/{id}/groups/{n}/tie-resolution` | admin |
| | `POST /api/v1/tournaments/{id}/matches/{tmId}/walkover` | admin |
| | `POST /api/v1/tournaments/{id}/sync-results` | admin |
| | `POST /api/v1/tournaments/{id}/knockout?allowSameGroupMatches=false` | admin |

## 5. Paginación

Los listados que pueden crecer devuelven una página en lugar de una lista:

```json
{ "content": [ ... ], "page": 0, "size": 10, "totalElements": 23, "totalPages": 3, "first": true, "last": false }
```

- Parámetros: `page` (desde 0, por defecto 0) y `size` (por defecto 10, máximo 50). Valores fuera de rango se ajustan.
- La paginación se hace en PostgreSQL (`LIMIT/OFFSET` con `Pageable`), no en memoria. El orden es fijo por endpoint.

| Endpoint | Orden |
|---|---|
| `GET /api/v1/clubs` | nombre ascendente |
| `GET /api/v1/admin/clubs/pending` | más antiguo primero (`updatedAt`) |
| `GET /api/v1/admin/clubs/{id}/reviews` | revisión más reciente primero |
| `GET /api/v1/club-memberships/clubs/{clubId}/pending` | solicitud más antigua primero |
| `GET /api/v1/club-memberships/clubs/{clubId}/members` | fecha de ingreso ascendente |
| `GET /api/v1/club-memberships/players/{playerId}` | más reciente primero |
| `GET /api/v1/tournaments/clubs/{clubId}` | torneo más reciente primero |

Siguen devolviendo lista (acotada a un solo torneo y necesaria completa para siembra y llave):
participantes, partidos y clasificación de grupos de un torneo.

## 6. Datos antiguos
`backfill-club-admin-memberships.sql` crea la membresía CLUB_ADMIN que falta en clubes aprobados antes de esta
funcionalidad. Es idempotente, solo inserta y **no se ha ejecutado**: requiere autorización.

## 7. Integración con Match (Adriana)

| Necesidad de Tournament | Estado |
|---|---|
| Registrar y validar marcadores (sets a 11, deuce, BO3/BO5/BO7) | Integrado: los jugadores usan `POST /api/v1/matches/{id}/submit` y `PUT /api/v1/matches/{id}/confirm` |
| Ganador, sets y estado del partido | Integrado: `MatchService.getMatchById` (`winnerId`, `sets`) |
| Avance automático de la llave | Integrado: `TournamentMatchEventListener` escucha `MatchConfirmedEvent` |
| Crear el partido de un torneo | **Provisional**: `MatchService.createMatch` todavía solo acepta FRIEND/COMMUNITY/LOCATION (FRIEND exige amistad). Mientras tanto, `ProvisionalMatchIntegrationAdapter` guarda el `Match` con `MatchRepository` (queda con el `matchType` por defecto, FRIEND). Ver el acuerdo abajo |
| Participantes correctos | Integrado: Tournament comprueba que el `Match` confirmado lo jugaron exactamente los dos jugadores del enfrentamiento (si vienen en orden inverso, reorienta sets y puntos); si no coinciden → 409 |
| Sin duplicados | Integrado: cada enfrentamiento tiene como máximo un `Match` (`match_id` único y `schedule` no crea un segundo) |
| W.O. | Integrado sin inventar sets ni puntos: gana el presente y los sets/puntos quedan vacíos. Se rechaza con 409 si ya hay un marcador reportado en Match (PROPOSED, CONFIRMED o DISPUTED) |
| Anular el `Match` de un W.O. | Pendiente: el `Match` queda en CREATED y Tournament lo ignora; `cancelMatch` exige que actúe un jugador participante y el W.O. lo declara el admin del torneo |

### Acuerdo con Adriana (Match)
- Match permitirá crear un partido entre dos jugadores inscritos en el mismo torneo, aunque no sean amigos, no
  compartan comunidad ni estén cerca. FRIEND, COMMUNITY y LOCATION se conservan; Tournament **no** agrega
  `MatchType.TOURNAMENT` por su cuenta.
- El admin de un club APPROVED crea el torneo e inscribe participantes; Tournament genera los enfrentamientos;
  Match gestiona sets y resultados; Tournament actualiza clasificación y llave tras la confirmación.
- INTERNAL: jugadores ACTIVE con membresía APPROVED en el club organizador. OPEN: cualquier jugador ACTIVE, aunque
  no pertenezca a un club. Inscribirse no crea membresía.
- Cuando Adriana publique el método en `main`, solo cambia `ProvisionalMatchIntegrationAdapter.createMatch`.

### Pendiente en Match (no se modificó desde esta rama)
`MatchServiceImpl.submitScore` hace `match.setSets(newSets)` sobre una colección con `orphanRemoval = true`.
Hibernate lanza `JpaSystemException: A collection with orphan deletion was no longer referenced...` y el endpoint
responde 500. Corrección sugerida: `match.getSets().clear(); match.getSets().addAll(newSets);`.
