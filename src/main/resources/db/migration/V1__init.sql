-- =====================================================================
-- Pulser - V1__init.sql
-- Esquema inicial coherente con las entidades JPA (Hibernate 7 / MySQL).
-- Pensado para arrancar con spring.jpa.hibernate.ddl-auto=validate.
-- Orden de creacion respetando dependencias de FK:
--   usuario -> evento -> tipo_entrada -> entrada -> check_in
-- =====================================================================

-- ---------------------------------------------------------------------
-- usuario
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    nombre     VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    rol        VARCHAR(30)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuario_email UNIQUE (email)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- evento
-- ---------------------------------------------------------------------
CREATE TABLE evento (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    nombre          VARCHAR(255) NOT NULL,
    descripcion     TEXT,
    recinto         VARCHAR(255) NOT NULL,
    ciudad          VARCHAR(255) NOT NULL,
    fecha_evento    DATETIME(6)  NOT NULL,
    categoria       VARCHAR(30)  NOT NULL,
    imagen_url      VARCHAR(255),
    estado          VARCHAR(30)  NOT NULL,
    organizador_id  BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_evento_organizador FOREIGN KEY (organizador_id) REFERENCES usuario (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- tipo_entrada
-- ---------------------------------------------------------------------
CREATE TABLE tipo_entrada (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    evento_id    BIGINT        NOT NULL,
    nombre       VARCHAR(255)  NOT NULL,
    precio       DECIMAL(38,2) NOT NULL,
    aforo        INTEGER       NOT NULL,
    vendidas     INTEGER       NOT NULL DEFAULT 0,
    descripcion  VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_tipo_entrada_evento FOREIGN KEY (evento_id) REFERENCES evento (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- entrada  (PK UUID en CHAR(36); version para optimistic locking)
-- ---------------------------------------------------------------------
CREATE TABLE entrada (
    id               CHAR(36)      NOT NULL,
    evento_id        BIGINT        NOT NULL,
    tipo_entrada_id  BIGINT        NOT NULL,
    usuario_id       BIGINT        NOT NULL,
    precio           DECIMAL(38,2) NOT NULL,
    estado           VARCHAR(30)   NOT NULL,
    codigo_qr        TEXT,
    version          BIGINT        NOT NULL,
    fecha_compra     DATETIME(6)   NOT NULL,
    fecha_uso        DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_entrada_evento       FOREIGN KEY (evento_id)       REFERENCES evento (id),
    CONSTRAINT fk_entrada_tipo_entrada FOREIGN KEY (tipo_entrada_id) REFERENCES tipo_entrada (id),
    CONSTRAINT fk_entrada_usuario      FOREIGN KEY (usuario_id)      REFERENCES usuario (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- check_in  (varios registros por entrada: validos y rechazos)
-- ---------------------------------------------------------------------
CREATE TABLE check_in (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    entrada_id  CHAR(36)     NOT NULL,
    `timestamp` DATETIME(6)  NOT NULL,
    resultado   VARCHAR(30)  NOT NULL,
    puerta      VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_check_in_entrada FOREIGN KEY (entrada_id) REFERENCES entrada (id)
) ENGINE=InnoDB;
