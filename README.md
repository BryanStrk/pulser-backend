# 🎫 Pulser · Backend

> Plataforma de venta de entradas para eventos con **tickets QR firmados criptográficamente** y **dashboard de control de acceso en tiempo real**.

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25"/>
  <img src="https://img.shields.io/badge/Spring_Boot-4.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 4"/>
  <img src="https://img.shields.io/badge/Spring_Security-7.1-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" alt="Spring Security 7.1"/>
  <img src="https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white" alt="MySQL"/>
  <img src="https://img.shields.io/badge/WebSocket-STOMP-010101?style=for-the-badge&logo=socketdotio&logoColor=white" alt="WebSocket STOMP"/>
  <img src="https://img.shields.io/badge/Tests-passing-success?style=for-the-badge&logo=junit5&logoColor=white" alt="Tests"/>
</p>

---

## 🚀 Descripción del proyecto

**Pulser** es el backend de una plataforma de ticketing para eventos: los organizadores publican eventos y definen tipos de entrada con aforo limitado; los asistentes compran entradas y reciben un **código QR firmado**; y en la puerta, el personal valida esos QR contra un **dashboard que se actualiza en vivo** a medida que la gente entra.

El proyecto no es un CRUD más. Se construyó alrededor de tres problemas que aparecen de verdad en un sistema de venta de entradas y que rara vez se resuelven bien:

- **Concurrencia sobre el aforo.** Dos personas compran el último asiento en el mismo milisegundo. Solo una puede ganar. Pulser lo garantiza con una operación atómica a nivel de base de datos, no con lógica de aplicación optimista que se rompe bajo carga.
- **Autenticidad de la entrada.** Un QR tiene que ser imposible de falsificar sin ser una base de datos de imágenes. Pulser firma cada entrada con **HMAC-SHA256**: el contenido viaja legible, pero nadie puede fabricar una entrada válida sin la clave secreta del servidor.
- **Doble uso en la puerta.** La misma entrada no puede pasar dos veces, ni siquiera si dos lectores la escanean a la vez. Se protege con un `UPDATE` condicional atómico que registra cada intento para auditoría.

El resultado es un backend que demuestra **atomicidad, condiciones de carrera, criptografía aplicada y comunicación en tiempo real** — construido bloque a bloque con revisión de código en cada fase.

---

## 🛠️ Tecnologías utilizadas

| Categoría | Tecnología |
|---|---|
| **Lenguaje** | Java 25 |
| **Framework** | Spring Boot 4 |
| **Seguridad** | Spring Security 7.1 · JWT (jjwt 0.12.6) |
| **Persistencia** | Spring Data JPA · Hibernate 7 · MySQL 8 |
| **Tiempo real** | WebSocket + STOMP (`spring-boot-starter-websocket`) |
| **Almacenamiento de imágenes** | Cloudinary |
| **Generación de QR** | ZXing |
| **Documentación API** | springdoc-openapi (Swagger UI) |
| **Testing** | JUnit 5 · Mockito · JaCoCo |
| **Build** | Maven (wrapper incluido) |

---

## 🏗️ Arquitectura y estructura

### Package-by-feature (vertical slices)

El código se organiza **por funcionalidad, no por capa técnica**. Cada dominio (`evento`, `entrada`, `checkin`, `usuario`) contiene sus propios controllers, services, repositories, DTOs y mappers. Esto mantiene el código de una misma funcionalidad junto y reduce el acoplamiento entre dominios.

```
src/main/java/com/bryanstrk/pulser/
├── config/                 # SecurityConfig, WebSocketConfig, OpenApiConfig, CloudinaryConfig
├── usuario/                # Registro, login y entidad de usuario
│   └── auth/               # AuthController, AuthService, DTOs
├── evento/                 # Eventos + tipos de entrada
│   ├── EstadoEventoTransicion   # Máquina de estados (BORRADOR → PUBLICADO → …)
│   ├── EventoService · TipoEntradaService
│   └── dto/
├── entrada/                # Compra de entradas, snapshot de precio y QR
│   ├── EntradaService · EntradaRepository (reserva atómica de aforo)
│   ├── QrImageService      # Render PNG on-demand con ZXing
│   └── dto/
├── checkin/                # Validación en puerta + feed en vivo
│   ├── CheckInService · CheckInFeedListener (@TransactionalEventListener)
│   └── event/              # Evento de dominio para el feed WebSocket
└── shared/
    ├── security/           # JwtService, JwtAuthFilter, QrSigningService, CurrentUserService
    ├── websocket/          # StompAuthChannelInterceptor, SubscriptionAuthorizationService
    └── exception/          # GlobalExceptionHandler + excepciones de dominio
```

### Componentes clave

- **`GlobalExceptionHandler`** — manejo centralizado de errores con mapeo semántico: `400` (validación), `401` (no autenticado), `403` (sin permiso), `404` (recurso no encontrado, *enmascarado* para no revelar existencia), `409` (conflicto de negocio) y `503` (servicio externo caído).
- **`EstadoEventoTransicion`** — máquina de estados que impide transiciones inválidas (p. ej. no se puede editar un evento `FINALIZADO`).
- **`QrSigningService`** — firma y verificación HMAC-SHA256 con comparación en **tiempo constante** (`MessageDigest.isEqual`) e instancia de `Mac` por llamada (thread-safe).
- **`CheckInFeedListener`** — emite al dashboard **solo tras el commit** de la transacción (`AFTER_COMMIT`), garantizando que nunca se muestra un check-in que un rollback deshizo.

### 🔒 Seguridad y concurrencia (la parte que importa)

**Control de aforo atómico.** La reserva de una plaza no lee-y-luego-escribe (patrón vulnerable a carreras). Es un único `UPDATE` condicional:

```sql
UPDATE tipo_entrada SET vendidas = vendidas + 1
WHERE id = :id AND vendidas < aforo
```

Si afecta 0 filas, el aforo está agotado → `409`. El predicado `vendidas < aforo` + el bloqueo de fila del propio `UPDATE` serializan las compras concurrentes a nivel de base de datos.

> **Verificado:** dos compras simultáneas del último asiento (aforo = 1) → una `201`, una `409`. En base de datos, `vendidas = 1` y `aforo = 1`. Cero overselling.

![Prueba de aforo atómico: vendidas=1, aforo=1 tras dos compras concurrentes](docs/aforo-concurrency.png)

**QR firmado, no cifrado.** El token tiene el formato `base64url(entradaId.eventoId.epochSeconds).base64url(HMAC)`. El contenido es legible a propósito — la seguridad está en que **nadie puede fabricar la firma sin la clave secreta**, no en ocultar los datos.

**Protección de doble uso.** Marcar una entrada como usada es también un `UPDATE` condicional (`... WHERE id = :id AND estado = 'VALIDA'`), de modo que dos validaciones concurrentes del mismo QR resultan en exactamente un `VALIDO` y un `YA_USADA`.

> **Verificado:** dos check-ins simultáneos del mismo QR → en la tabla `check_in`, una fila `VALIDO` y una `YA_USADA`. La entrada queda `USADA` con `version` incrementada una sola vez.

![Prueba de doble-uso: una fila VALIDO y una YA_USADA en check_in](docs/checkin-concurrency.png)

**Autenticación del WebSocket.** El handshake STOMP no lleva cabecera `Authorization`; el JWT viaja en el frame `CONNECT` y se valida en un interceptor de canal. Además, la suscripción a `/topic/eventos/{id}/checkins` se **autoriza en el servidor**: solo el organizador de ese evento (o un ADMIN) puede escuchar su feed.

---

## ⚙️ Requisitos previos e instalación

### Requisitos de entorno

- **JDK 25**
- **MySQL 8** en ejecución
- Cuenta de **Cloudinary** (para subir imágenes de eventos)
- Maven — no hace falta instalarlo, el proyecto incluye el wrapper (`./mvnw`)

### 1. Clonar el repositorio

```bash
git clone https://github.com/BryanStrk/pulser-backend.git
cd pulser-backend
```

### 2. Crear la base de datos

```sql
CREATE DATABASE pulser CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. Configurar las variables de entorno

El proyecto **no contiene ninguna credencial en el código**. Todos los secretos se inyectan por variables de entorno (referenciadas como `${VARIABLE}` en `application.yaml`). Configúralas en tu shell, en un archivo `.env`, o en las *Run Configurations* de tu IDE:

| Variable | Descripción | Ejemplo |
|---|---|---|
| `DB_URL` | URL JDBC de MySQL | `jdbc:mysql://localhost:3306/pulser` |
| `DB_USERNAME` | Usuario de la base de datos | `[TU_DB_USERNAME]` |
| `DB_PASSWORD` | Contraseña de la base de datos | `[TU_DB_PASSWORD]` |
| `JWT_SECRET` | Clave para firmar los JWT (base64) | *genérala, ver abajo* |
| `QR_HMAC_SECRET` | Clave HMAC para firmar los QR (base64) | *genérala, ver abajo* |
| `CLOUDINARY_CLOUD_NAME` | Nombre de tu cloud en Cloudinary | `[TU_CLOUD_NAME]` |
| `CLOUDINARY_API_KEY` | API key de Cloudinary | `[TU_API_KEY]` |
| `CLOUDINARY_API_SECRET` | API secret de Cloudinary | `[TU_API_SECRET]` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos, separados por comas | `http://localhost:4200` |

> ⚠️ **Genera los secretos, no los inventes.** Usa una clave aleatoria robusta para cada uno:
> ```bash
> openssl rand -base64 32
> ```

### 4. Ejecutar

```bash
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080/api` y la documentación interactiva en:

```
http://localhost:8080/api/swagger-ui.html
```

---

## 🗺️ Endpoints principales (API REST)

> Rutas relativas al context-path `/api`. Los endpoints de escritura requieren un JWT válido (`Authorization: Bearer <token>`).

### 🔐 Autenticación

| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/auth/register` | Registrar un usuario | Público |
| `POST` | `/auth/login` | Iniciar sesión y obtener JWT | Público |

### 🎪 Eventos

| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `GET` | `/eventos` | Listar eventos publicados (paginado, filtros) | Público |
| `GET` | `/eventos/{id}` | Detalle de un evento | Público |
| `GET` | `/eventos/mis-eventos` | Eventos del organizador actual (cualquier estado) | Organizador / Admin |
| `POST` | `/eventos` | Crear un evento (nace en `BORRADOR`) | Organizador / Admin |
| `PUT` | `/eventos/{id}` | Editar un evento (no si es terminal) | Dueño / Admin |
| `PATCH` | `/eventos/{id}/estado` | Cambiar estado (máquina de transiciones) | Dueño / Admin |
| `POST` | `/eventos/{id}/imagen` | Subir imagen del evento (Cloudinary) | Dueño / Admin |
| `DELETE` | `/eventos/{id}` | Eliminar (solo en `BORRADOR`) | Dueño / Admin |

### 🎟️ Tipos de entrada

| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/eventos/{id}/tipos-entrada` | Crear un tipo de entrada con aforo | Dueño / Admin |
| `PUT` | `/tipos-entrada/{id}` | Editar (nuevo aforo ≥ vendidas) | Dueño / Admin |
| `DELETE` | `/tipos-entrada/{id}` | Eliminar (solo si 0 vendidas) | Dueño / Admin |

### 🎫 Entradas

| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/eventos/{id}/entradas` | Comprar una entrada (reserva atómica + QR firmado) | Autenticado |
| `GET` | `/entradas/mis-entradas` | Entradas del usuario actual | Autenticado |
| `GET` | `/entradas/{id}` | Detalle de una entrada | Comprador / Admin |
| `GET` | `/entradas/{id}/qr` | Imagen PNG del QR (render on-demand) | Comprador / Admin |

### ✅ Check-in

| Método | Endpoint | Descripción | Acceso |
|---|---|---|---|
| `POST` | `/checkins` | Validar un QR en puerta (emite al feed en vivo) | Organizador / Admin |

### 📡 WebSocket (STOMP)

| Destino | Descripción |
|---|---|
| `CONNECT /ws` | Handshake STOMP; el JWT viaja en el frame CONNECT |
| `SUBSCRIBE /topic/eventos/{id}/checkins` | Feed de check-ins en vivo (solo organizador del evento / Admin) |

---

## 🧪 Pruebas (testing)

El proyecto incluye una batería completa de tests unitarios (Mockito puro, sin levantar el contexto de Spring) que cubre la lógica de negocio crítica: ownership, máquina de estados, control de aforo, firma HMAC, doble uso concurrente y autorización de suscripciones WebSocket.

```bash
# Toda la batería
./mvnw test

# Un suite concreto
./mvnw test -Dtest=EntradaServiceTest

# Con reporte de cobertura (JaCoCo)
./mvnw verify
```

### Verificación de concurrencia (end-to-end)

Más allá de los tests unitarios, el directorio `scripts/` contiene scripts de verificación que prueban las garantías de concurrencia **contra la aplicación real**:

```bash
# Doble compra del último asiento y validación de QR en puerta
./scripts/verify-checkin.sh

# Cliente STOMP de línea de comandos para el feed en vivo
cd scripts && npm install && cd ..
TOKEN=<jwt> EVENTO_ID=<id> node scripts/ws-checkin-client.js
```

---

## 🤝 Contribuciones

Este es un proyecto personal de portfolio, pero las sugerencias son bienvenidas:

1. Haz un fork del repositorio.
2. Crea una rama para tu cambio (`git checkout -b feat/mi-mejora`).
3. Usa [Conventional Commits](https://www.conventionalcommits.org/) en inglés (`feat:`, `fix:`, `test:`, `chore:`…).
4. Asegúrate de que `./mvnw test` pasa en verde.
5. Abre un Pull Request describiendo el cambio.

---

## 📄 Licencia

Distribuido bajo la licencia **MIT**. Consulta el archivo [`LICENSE`](LICENSE) para más detalles.

---

<p align="center">
  Desarrollado por <a href="https://github.com/BryanStrk">Bryan Paico</a> ·
  <a href="https://bryanpaico.com">bryanpaico.com</a>
</p>
