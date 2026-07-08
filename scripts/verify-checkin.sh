#!/usr/bin/env bash
# Verificacion manual del bloque CHECK-IN. Requiere: app corriendo en :8080 con context-path /api,
# QR_HMAC_SECRET seteado, DB arrancada, y `jq` instalado.
# Uso: bash scripts/verify-checkin.sh
set -euo pipefail

BASE="http://localhost:8080/api"
TS=$(date +%s)
ORG_EMAIL="org.checkin.${TS}@pulser.test"
ORG2_EMAIL="org2.checkin.${TS}@pulser.test"
PASS="password123"

echo "== 1) Registro/login ORGANIZADOR dueño =="
ORG_TOKEN=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"nombre\":\"Org\",\"email\":\"$ORG_EMAIL\",\"password\":\"$PASS\",\"rol\":\"ORGANIZADOR\"}" \
  | jq -r '.token')
echo "ORG token: ${ORG_TOKEN:0:20}..."

echo "== 1b) Segundo ORGANIZADOR (ajeno, para el 403) =="
ORG2_TOKEN=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"nombre\":\"Org2\",\"email\":\"$ORG2_EMAIL\",\"password\":\"$PASS\",\"rol\":\"ORGANIZADOR\"}" \
  | jq -r '.token')

echo "== 2) Crear evento + publicar + tipo de entrada (como dueño) =="
EVENTO_ID=$(curl -s -X POST "$BASE/eventos" -H "Authorization: Bearer $ORG_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"nombre\":\"CheckIn Test\",\"recinto\":\"WiZink\",\"ciudad\":\"Madrid\",\"fechaEvento\":\"2030-01-01T20:00:00\",\"categoria\":\"CONCIERTO\"}" \
  | jq -r '.id')
echo "eventoId: $EVENTO_ID"

curl -s -X PATCH "$BASE/eventos/$EVENTO_ID/estado" -H "Authorization: Bearer $ORG_TOKEN" -H 'Content-Type: application/json' \
  -d '{"nuevoEstado":"PUBLICADO"}' >/dev/null

TIPO_ID=$(curl -s -X POST "$BASE/eventos/$EVENTO_ID/tipos-entrada" -H "Authorization: Bearer $ORG_TOKEN" -H 'Content-Type: application/json' \
  -d '{"nombre":"General","precio":30.00,"aforo":100}' | jq -r '.id')
echo "tipoEntradaId: $TIPO_ID"

# Helper: compra una entrada (como el propio dueño, que tambien puede comprar) y devuelve su codigoQr.
comprar_token() {
  curl -s -X POST "$BASE/eventos/$EVENTO_ID/entradas" -H "Authorization: Bearer $ORG_TOKEN" -H 'Content-Type: application/json' \
    -d "{\"tipoEntradaId\":$TIPO_ID}" | jq -r '.codigoQr'
}
checkin() { # $1=token $2=jwt  -> imprime cuerpo + codigo HTTP
  curl -s -w '\nHTTP %{http_code}\n' -X POST "$BASE/checkins" -H "Authorization: Bearer $2" -H 'Content-Type: application/json' \
    -d "{\"token\":\"$1\",\"puerta\":\"Puerta A\"}"
}

echo "== 3) Primer check-in de una entrada VALIDA -> esperado VALIDO =="
QR1=$(comprar_token)
checkin "$QR1" "$ORG_TOKEN"

echo "== 4) Segundo check-in de la MISMA -> esperado YA_USADA =="
checkin "$QR1" "$ORG_TOKEN"

echo "== 5) Token con firma rota (ultimo char cambiado) -> esperado INVALIDA (entradaId null) =="
BROKEN="${QR1%?}X"
checkin "$BROKEN" "$ORG_TOKEN"

echo "== 6) Organizador AJENO valida entrada de este evento -> esperado HTTP 403 (sin auditar) =="
QR_403=$(comprar_token)
checkin "$QR_403" "$ORG2_TOKEN"

echo "== 7) DOBLE-USO CONCURRENTE: dos check-ins simultaneos del MISMO token valido =="
echo "   esperado: exactamente UN VALIDO y UN YA_USADA (nunca dos VALIDO)"
QR_RACE=$(comprar_token)
checkin "$QR_RACE" "$ORG_TOKEN" > /tmp/checkin_a.txt &
checkin "$QR_RACE" "$ORG_TOKEN" > /tmp/checkin_b.txt &
wait
echo "--- respuesta A ---"; cat /tmp/checkin_a.txt
echo "--- respuesta B ---"; cat /tmp/checkin_b.txt
echo "--- recuento de 'resultado' entre las dos respuestas concurrentes ---"
cat /tmp/checkin_a.txt /tmp/checkin_b.txt | grep -o '"resultado":"[A-Z_]*"' | sort | uniq -c
echo "   (debe listar: 1 VALIDO y 1 YA_USADA)"
