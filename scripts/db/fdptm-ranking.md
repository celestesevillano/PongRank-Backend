# Padrón FDPTM

Iniciar Backend una vez para crear las tablas mediante JPA. Desde la carpeta que contiene el CSV revisado, ejecutar `psql -f /ruta/al/repo/scripts/db/fdptm-ranking-import.sql` con la conexión al entorno previsto.

El CSV debe estar en UTF-8, llamarse `fdptm-ranking.csv` y tener estas columnas, en orden:

```csv
name,club,category,ranking_date,ranking_position
Ejemplo Ficticio,Club de ejemplo,Damas todo competidor,2026-09-15,1
```

La fecha, categoría y puesto identifican una entrada. Reimportar la misma versión es seguro. Si cambia una fila de la misma versión, la transacción falla para preservar la referencia de las revisiones existentes.

## API

- `GET /api/v1/players/me/fdptm-review` devuelve el estado propio o 404 si no hay solicitud.
- `GET /api/v1/players/{playerId}/fdptm-verification` devuelve `{ "verified": true }` solo para una solicitud aprobada.
- `GET /api/v1/admin/fdptm/reviews?status=PENDING&page=0&size=20` lista solicitudes por estado.
- `GET /api/v1/admin/fdptm/ranking?category=...&name=...` busca candidatos. Usar la categoría declarada y el nombre publicado, que puede figurar en otro orden.
- `PATCH /api/v1/admin/fdptm/reviews/{playerId}/verify` recibe `{ "rankingEntryId": 1 }`.
- `PATCH /api/v1/admin/fdptm/reviews/{playerId}/reject` recibe `{ "reason": "Motivo de la decisión" }`.

La administración coteja la identidad antes de aprobar. El ranking solo sirve como evidencia de revisión. Las declaraciones históricas sin solicitud conservan su estado no verificado.
