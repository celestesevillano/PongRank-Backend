#!/usr/bin/env bash
# =============================================================================
# PongRank - Integration test for Club, ClubMembership and Tournament (HTTP + PostgreSQL)
#
# Runs against an ISOLATED database (default: pongrank_it) so your real data is never touched.
# Match results go through the REAL Match module endpoints (submit + confirm, ITTF validation) and the
# bracket must advance automatically through MatchConfirmedEvent. Any unexpected HTTP 500 fails the run.
#
# Usage (from the project root, with the app already running against the isolated DB):
#   ./docs/club-tournament/integration-test.sh [BASE_URL] [DB_CONTAINER] [DB_NAME]
# =============================================================================
set -u

BASE_URL="${1:-http://localhost:8081}"
DB_CONTAINER="${2:-pongrank-postgres}"
DB_NAME="${3:-pongrank_it}"
RUN_ID="$(date +%s)"
PASS=0
FAIL=0
BODY=""
STATUS=""

json() { python3 -c "import sys, json; d = json.load(sys.stdin); print(eval(sys.argv[1], {'d': d}))" "$1"; }
sql() { docker exec -i "$DB_CONTAINER" psql -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 -qAt -c "$1"; }

# request METHOD PATH [JSON_BODY]
request() {
  local method="$1" path="$2" body="${3:-}"
  local response
  if [ -n "$body" ]; then
    response=$(curl -s -w $'\n%{http_code}' -X "$method" "$BASE_URL$path" -H "Content-Type: application/json" -d "$body")
  else
    response=$(curl -s -w $'\n%{http_code}' -X "$method" "$BASE_URL$path")
  fi
  STATUS="${response##*$'\n'}"
  BODY="${response%$'\n'*}"
}

# check DESCRIPTION EXPECTED_HTTP_CODE  (exact code only: a 500 is never accepted)
check() {
  local description="$1" expected="$2" ok=0
  [ "$STATUS" = "$expected" ] && [ "$STATUS" != "500" ] && ok=1
  if [ $ok -eq 1 ]; then
    PASS=$((PASS + 1)); echo "  PASS [$STATUS] $description"
  else
    FAIL=$((FAIL + 1)); echo "  FAIL [$STATUS, expected $expected] $description"; echo "       $BODY" | head -c 400; echo
  fi
}

assert_eq() {
  local description="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    PASS=$((PASS + 1)); echo "  PASS $description"
  else
    FAIL=$((FAIL + 1)); echo "  FAIL $description (expected '$expected', got '$actual')"
  fi
}

register() {
  request POST /api/v1/players/register "{\"name\":\"$1 $RUN_ID\",\"email\":\"$1.$RUN_ID@it.pongrank.test\",\"password\":\"Test12345\"}"
  echo "$BODY" | json "d['id']"
}

play_match() { # play_match MATCH_ID PLAYER1_ID PLAYER2_ID -> player1 wins 2-0 (BO3) through the Match module
  request POST "/api/v1/matches/$1/submit?submittingPlayerId=$2" '{"sets":[{"setNumber":1,"scorePlayer1":11,"scorePlayer2":5},{"setNumber":2,"scorePlayer1":12,"scorePlayer2":10}]}'
  check "Match $1: player1 submits score" 200
  request PUT "/api/v1/matches/$1/confirm?actingPlayerId=$3"
  check "Match $1: player2 confirms" 200
}

play_scheduled() { # play every SCHEDULED match of the given stage in tournament $T
  local stage="$1" rows
  rows=$(curl -s "$BASE_URL/api/v1/tournaments/$T/matches" | json "' '.join(f\"{m['matchId']},{m['player1']['id']},{m['player2']['id']}\" for m in d if m['status']=='SCHEDULED' and m['stage']=='$stage')")
  for row in $rows; do
    IFS=, read -r MID P1 P2 <<< "$row"
    play_match "$MID" "$P1" "$P2"
  done
}

echo "== PongRank integration test (run $RUN_ID) against $BASE_URL, database $DB_NAME"
request GET /api/v1/clubs
if [ -z "$STATUS" ] || [ "$STATUS" = "000" ]; then echo "The application is not reachable at $BASE_URL"; exit 1; fi

echo "== 1. Players"
SA=$(register sysadmin); A=$(register admin); B=$(register bruno); C=$(register carla); D=$(register diego)
E=$(register elena); F=$(register fabio); G=$(register gabi); H=$(register hugo)
sql "UPDATE players SET role='ROLE_SYSTEM_ADMIN' WHERE id=$SA;"
sql "UPDATE players SET rating_glicko=1900 WHERE id=$B; UPDATE players SET rating_glicko=1800 WHERE id=$C;"
echo "  players: SA=$SA A=$A B=$B C=$C D=$D E=$E F=$F G=$G H=$H"

echo "== 2. Club verification"
request GET "/api/v1/clubs/999999999"
check "unknown club -> 404" 404
request GET "/api/v1/tournaments/999999999"
check "unknown tournament -> 404" 404
request POST "/api/v1/tournaments?actingPlayerId=$A" '{"clubId":1,"name":"Fechas","type":"OPEN","matchFormat":"BO3","startDate":"2026-10-10T10:00:00","endDate":"2026-10-01T10:00:00"}'
check "end date before start date -> 400" 400
request POST "/api/v1/clubs?actingPlayerId=$A" "{\"name\":\"Club IT $RUN_ID\",\"address\":\"Av. Test 1\",\"affiliationDocumentUrl\":\"https://docs.test/$RUN_ID-1.pdf\"}"
check "register club -> PENDING" 201; CLUB=$(echo "$BODY" | json "d['id']")
assert_eq "new club status" "PENDING" "$(echo "$BODY" | json "d['status']")"
request POST "/api/v1/clubs?actingPlayerId=$A" "{\"name\":\"club it $RUN_ID\",\"address\":\"x\",\"affiliationDocumentUrl\":\"https://docs.test/x.pdf\"}"
check "same admin cannot register a second club / duplicate name" 409
request PATCH "/api/v1/admin/clubs/$CLUB/approve?actingPlayerId=$B"
check "non system admin cannot approve -> 403" 403
request PATCH "/api/v1/admin/clubs/$CLUB/reject?actingPlayerId=$SA" '{"rejectionReason":""}'
check "reject without reason -> 400" 400
request PATCH "/api/v1/admin/clubs/$CLUB/reject?actingPlayerId=$SA" '{"rejectionReason":"Documento vencido"}'
check "system admin rejects with reason" 200
request GET "/api/v1/clubs/$CLUB"
check "public detail" 200
assert_eq "public detail hides rejection reason" "False" "$(echo "$BODY" | json "'rejectionReason' in d or 'affiliationDocumentUrl' in d")"
request GET "/api/v1/clubs/$CLUB/review?actingPlayerId=$A"
check "club admin reads private review" 200
assert_eq "private review shows reason" "Documento vencido" "$(echo "$BODY" | json "d['rejectionReason']")"
request GET "/api/v1/clubs/$CLUB/review?actingPlayerId=$B"
check "other player cannot read private review -> 403" 403
request PATCH "/api/v1/clubs/$CLUB/resubmit?actingPlayerId=$A" "{\"affiliationDocumentUrl\":\"https://docs.test/$RUN_ID-2.pdf\"}"
check "resubmit rejected club -> PENDING" 200
assert_eq "resubmit keeps the same club id" "$CLUB" "$(echo "$BODY" | json "d['id']")"
request POST "/api/v1/tournaments?actingPlayerId=$A" "{\"clubId\":$CLUB,\"name\":\"T0\",\"type\":\"INTERNAL\",\"matchFormat\":\"BO3\"}"
check "PENDING club cannot create tournaments -> 409" 409
request PATCH "/api/v1/admin/clubs/$CLUB/approve?actingPlayerId=$SA"
check "system admin approves" 200
request GET "/api/v1/admin/clubs/$CLUB/reviews?actingPlayerId=$SA"
check "review history" 200
assert_eq "history keeps rejection and approval" "2" "$(echo "$BODY" | json "d['totalElements']")"
request GET "/api/v1/club-memberships/clubs/$CLUB/members"
assert_eq "admin is the first member with role CLUB_ADMIN" "CLUB_ADMIN" "$(echo "$BODY" | json "[m['role'] for m in d['content'] if m['player']['id']==$A][0]")"

echo "== 3. Memberships"
for P in $B $C $D $E $F; do
  request POST "/api/v1/club-memberships?actingPlayerId=$P" "{\"clubId\":$CLUB}"
  check "player $P requests membership" 201
  MID=$(echo "$BODY" | json "d['id']")
  if [ "$P" = "$B" ]; then
    request POST "/api/v1/club-memberships?actingPlayerId=$P" "{\"clubId\":$CLUB}"
    check "duplicate request rejected -> 409" 409
    request PATCH "/api/v1/club-memberships/$MID/approve?actingPlayerId=$B"
    check "only the club admin approves -> 403" 403
  fi
  request PATCH "/api/v1/club-memberships/$MID/approve?actingPlayerId=$A"
  check "club admin approves $P" 200
  [ "$P" = "$B" ] && { request PATCH "/api/v1/club-memberships/$MID/approve?actingPlayerId=$A"; check "approving twice fails -> 409" 409; request PATCH "/api/v1/club-memberships/$MID/cancel?actingPlayerId=$B"; check "cannot cancel an approved membership -> 409" 409; }
done
request POST "/api/v1/club-memberships?actingPlayerId=$G" "{\"clubId\":$CLUB}"
GMID=$(echo "$BODY" | json "d['id']")
request PATCH "/api/v1/club-memberships/$GMID/cancel?actingPlayerId=$G"
check "player cancels own pending request" 200
request PATCH "/api/v1/club-memberships/$GMID/reject?actingPlayerId=$A"
check "cannot reject an already cancelled request -> 409" 409
ADMIN_MID=$(sql "SELECT id FROM club_memberships WHERE player_id=$A AND club_id=$CLUB AND status='APPROVED';")
request PATCH "/api/v1/club-memberships/$ADMIN_MID/leave?actingPlayerId=$A"
check "club admin cannot leave while responsible -> 409" 409
request POST "/api/v1/clubs?actingPlayerId=$G" "{\"name\":\"Club IT2 $RUN_ID\",\"address\":\"Av. Test 2\",\"affiliationDocumentUrl\":\"https://docs.test/$RUN_ID-3.pdf\"}"
CLUB2=$(echo "$BODY" | json "d['id']")
request POST "/api/v1/club-memberships?actingPlayerId=$B" "{\"clubId\":$CLUB2}"
check "request to a non approved club fails -> 409" 409

echo "== 4. Tournament (INTERNAL, 6 members -> 2 groups of 3)"
request POST "/api/v1/tournaments?actingPlayerId=$B" "{\"clubId\":$CLUB,\"name\":\"Apertura $RUN_ID\",\"type\":\"INTERNAL\",\"matchFormat\":\"BO3\"}"
check "non admin cannot create tournament -> 403" 403
request POST "/api/v1/tournaments?actingPlayerId=$A" "{\"clubId\":$CLUB,\"name\":\"Apertura $RUN_ID\",\"type\":\"INTERNAL\",\"matchFormat\":\"BO3\"}"
check "club admin creates INTERNAL tournament" 201; T=$(echo "$BODY" | json "d['id']")
request POST "/api/v1/tournaments/$T/participants?actingPlayerId=$A" "{\"playerId\":$H}"
check "non member cannot join INTERNAL tournament -> 409" 409
for P in $A $B $C $D $E $F; do
  request POST "/api/v1/tournaments/$T/participants?actingPlayerId=$A" "{\"playerId\":$P}"
  check "add participant $P" 201
done
request POST "/api/v1/tournaments/$T/participants?actingPlayerId=$A" "{\"playerId\":$B}"
check "duplicate participant rejected -> 409" 409
request GET "/api/v1/tournaments/$T/participants"
assert_eq "default seeding by Glicko: B (1900) is seed 1" "$B" "$(echo "$BODY" | json "d[0]['player']['id']")"
request POST "/api/v1/tournaments/$T/start?actingPlayerId=$B"
check "non admin cannot start -> 403" 403
request POST "/api/v1/tournaments/$T/start?actingPlayerId=$A"
check "start tournament -> GROUP_STAGE" 200
request POST "/api/v1/tournaments/$T/participants?actingPlayerId=$A" "{\"playerId\":$G}"
check "participant list closed after start -> 409" 409
request GET "/api/v1/tournaments/$T/matches"
assert_eq "6 round robin matches (2 groups of 3)" "6" "$(echo "$BODY" | json "len(d)")"

echo "== 5. Group results (1 W.O. via API, the rest played through the Match module)"
WO_TM=$(echo "$BODY" | json "d[0]['id']"); WO_ABSENT=$(echo "$BODY" | json "d[0]['player2']['id']")
request POST "/api/v1/tournaments/$T/matches/$WO_TM/walkover?actingPlayerId=$A" "{\"absentPlayerId\":$H}"
check "W.O. with a player outside the match -> 400" 400
request POST "/api/v1/tournaments/$T/matches/$WO_TM/walkover?actingPlayerId=$A" "{\"absentPlayerId\":$WO_ABSENT}"
check "declare W.O." 200
assert_eq "W.O. has no invented sets" "None" "$(echo "$BODY" | json "d.get('setsPlayer1')")"
request POST "/api/v1/tournaments/$T/knockout?actingPlayerId=$A"
check "knockout blocked while group matches are pending -> 409" 409
play_scheduled GROUP
request GET "/api/v1/tournaments/$T/groups"
check "group standings" 200
assert_eq "groups completed automatically after Match confirmations (no sync)" "True" "$(echo "$BODY" | json "all(g['completed'] for g in d)")"
assert_eq "2 qualifiers per group" "4" "$(echo "$BODY" | json "sum(1 for g in d for r in g['standings'] if r['qualified'])")"
request POST "/api/v1/tournaments/$T/sync-results?actingPlayerId=$A"
check "manual sync is still available (no-op)" 200

echo "== 6. Knockout"
request POST "/api/v1/tournaments/$T/knockout?actingPlayerId=$A"
check "generate knockout" 200
assert_eq "status KNOCKOUT_STAGE" "KNOCKOUT_STAGE" "$(echo "$BODY" | json "d['status']")"
request GET "/api/v1/tournaments/$T/matches"
assert_eq "2 semifinals scheduled" "2" "$(echo "$BODY" | json "sum(1 for m in d if m['stage']=='KNOCKOUT' and m['round']==1 and m['status']=='SCHEDULED')")"
play_scheduled KNOCKOUT
request GET "/api/v1/tournaments/$T/matches"
assert_eq "final scheduled automatically with both winners" "SCHEDULED" "$(echo "$BODY" | json "[m['status'] for m in d if m['stage']=='KNOCKOUT' and m['round']==2][0]")"
play_scheduled KNOCKOUT
request GET "/api/v1/tournaments/$T"
assert_eq "tournament FINISHED automatically" "FINISHED" "$(echo "$BODY" | json "d['status']")"
assert_eq "tournament has a champion" "True" "$(echo "$BODY" | json "d['winner'] is not None")"

echo "== 7. Pagination"
request GET "/api/v1/clubs?page=0&size=1"
check "approved clubs, page 0 size 1" 200
assert_eq "page size respected" "1" "$(echo "$BODY" | json "len(d['content'])")"
assert_eq "public page only contains APPROVED clubs" "True" "$(echo "$BODY" | json "all(c['status']=='APPROVED' for c in d['content'])")"
request GET "/api/v1/clubs?size=1000"
assert_eq "page size capped at 50" "50" "$(echo "$BODY" | json "d['size']")"
request GET "/api/v1/club-memberships/clubs/$CLUB/members?page=0&size=4"
assert_eq "6 active members in total" "6" "$(echo "$BODY" | json "d['totalElements']")"
assert_eq "first page has 4 members" "4" "$(echo "$BODY" | json "len(d['content'])")"
request GET "/api/v1/club-memberships/clubs/$CLUB/members?page=1&size=4"
assert_eq "second page has the remaining 2" "2" "$(echo "$BODY" | json "len(d['content'])")"
assert_eq "second page is the last" "True" "$(echo "$BODY" | json "d['last']")"
request GET "/api/v1/tournaments/clubs/$CLUB?page=0&size=5"
check "club tournaments paginated" 200
assert_eq "club has 1 tournament" "1" "$(echo "$BODY" | json "d['totalElements']")"
request GET "/api/v1/admin/clubs/pending?actingPlayerId=$SA&page=0&size=50"
check "pending clubs paginated (system admin)" 200
assert_eq "pending page has results and only PENDING clubs" "True" "$(echo "$BODY" | json "d['totalElements'] >= 1 and all(c['status']=='PENDING' for c in d['content'])")"
request GET "/api/v1/admin/clubs/pending?actingPlayerId=$B&page=0&size=5"
check "pending clubs still require the system admin -> 403" 403
request GET "/api/v1/club-memberships/clubs/$CLUB/pending?actingPlayerId=$A&page=0&size=5"
check "pending requests paginated (club admin)" 200

echo "== 8. Transfer and leave"
request PATCH "/api/v1/clubs/$CLUB/admin?actingPlayerId=$A" "{\"newAdminPlayerId\":$H}"
check "cannot transfer to a non member -> 409" 409
request PATCH "/api/v1/clubs/$CLUB/admin?actingPlayerId=$A" "{\"newAdminPlayerId\":$B}"
check "transfer administration to member B" 200
request PATCH "/api/v1/club-memberships/$ADMIN_MID/leave?actingPlayerId=$A"
check "former admin can leave after transfer" 200
request GET "/api/v1/club-memberships/players/$A"
assert_eq "history keeps LEFT membership" "LEFT" "$(echo "$BODY" | json "d['content'][0]['status']")"

echo
echo "== RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
