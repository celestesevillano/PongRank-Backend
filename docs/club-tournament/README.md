# Club, ClubMembership y Tournament — guía de verificación

Responsable: Emiliano (integrante 4). Rama: `feature/club-tournament-emiliano`.

> `actingPlayerId` es PROVISIONAL: lo envía el cliente y **no es autenticación**. Debe reemplazarse por la
> identidad del JWT (buscar `TODO: Replace actingPlayerId`). Mientras no se integren las excepciones de
> Celeste, los conflictos de negocio responden **500** (los errores de validación ya responden 400).

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
El puerto 5435 viene de tu `docker-compose.override.yml`. Los resultados de partidos se **simulan con SQL**
en la base aislada porque el módulo Match aún no tiene servicio: esto valida Tournament y el adaptador
provisional, **no** la lógica real de Match.

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
