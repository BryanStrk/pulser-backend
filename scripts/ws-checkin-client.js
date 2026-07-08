#!/usr/bin/env node
/*
 * Cliente STOMP de linea de comandos para verificar el feed de check-in en vivo (sin frontend).
 *
 * Requisitos (una vez):
 *   cd scripts && npm init -y && npm install @stomp/stompjs ws
 *
 * Uso:
 *   TOKEN=<jwt> EVENTO_ID=<id> node scripts/ws-checkin-client.js
 * Variables opcionales:
 *   WS_URL   (default ws://localhost:8080/api/ws)
 *
 * Que observar:
 *   - Con el token del ORGANIZADOR del evento (o ADMIN): "connected" + "SUBSCRIBED" y, al hacer un
 *     POST /checkins de ese evento, llega un frame MESSAGE con el CheckInFeedDto.
 *   - Con el token de OTRO usuario (no organizador ni admin): al suscribirse llega un ERROR frame
 *     (onStompError) -> suscripcion rechazada en servidor.
 *   - Sin token o token invalido: el CONNECT no llega a "connected" (ERROR / cierre) -> handshake
 *     STOMP no completado.
 */
const { Client } = require('@stomp/stompjs');
const WebSocket = require('ws');

const WS_URL = process.env.WS_URL || 'ws://localhost:8080/api/ws';
const TOKEN = process.env.TOKEN || '';
const EVENTO_ID = process.env.EVENTO_ID;

if (!EVENTO_ID) {
  console.error('Falta EVENTO_ID. Uso: TOKEN=<jwt> EVENTO_ID=<id> node scripts/ws-checkin-client.js');
  process.exit(1);
}

const destino = `/topic/eventos/${EVENTO_ID}/checkins`;

const client = new Client({
  webSocketFactory: () => new WebSocket(WS_URL),
  // El token viaja en la cabecera del frame CONNECT (no en la URL): asi lo valida el interceptor.
  connectHeaders: TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {},
  debug: (msg) => console.log('[stomp]', msg),
  reconnectDelay: 0, // sin reconexion automatica: queremos ver el fallo tal cual
});

client.onConnect = (frame) => {
  console.log('>> CONNECTED (handshake STOMP completado)');
  client.subscribe(destino, (message) => {
    console.log(`>> MESSAGE en ${destino}:`);
    console.log(message.body);
  });
  console.log(`>> SUBSCRIBED a ${destino} (si no llega ERROR, la suscripcion fue autorizada)`);
};

client.onStompError = (frame) => {
  console.error('>> ERROR frame del servidor:', frame.headers['message']);
  console.error('   body:', frame.body);
};

client.onWebSocketClose = (evt) => {
  console.error(`>> WebSocket cerrado (code=${evt.code})`);
};

console.log(`Conectando a ${WS_URL} y suscribiendo a ${destino} ...`);
client.activate();

process.on('SIGINT', () => { client.deactivate(); process.exit(0); });
