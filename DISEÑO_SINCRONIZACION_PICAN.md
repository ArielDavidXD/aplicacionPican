# DISEÑO DE SINCRONIZACIÓN — PICAN (Etapa 4.15)

Versión del protocolo: **1.0** · Versión de esquema soportada: **V1 + V2** · Estado: **implementado y verificado en PostgreSQL (14/14 comprobaciones)**.

> Decisiones empresariales no aprobadas (autoridad de escritura, resolución de conflictos, frecuencia, distribución central→UEB) se listan en §8. **No se inventan**. El motor implementa transporte, validación, idempotencia, detección de conflictos y confirmación; no decide qué versión prevalece cuando hay discrepancia no compatible.

---

## 1. Arquitectura y supuestos

- 25 bases PostgreSQL locales (una por UEB) + 1 base central de la Dirección de Producción, mismo esquema.
- Cada UEB opera sin conexión permanente con la central. El intercambio es manual mediante archivos ZIP transportados en memoria USB.
- Las 28 unidades no estatales **no** tienen PICAN, usuarios ni base: entregan partes en papel y la UEB los transcribe. No existe flujo directo con esas unidades; su relación con UEB está pendiente.
- `DatabaseConfig.getModo()` (`LOCAL`/`CENTRAL`) existe pero no gira ninguna regla de negocio: la dirección del flujo se decide por el **tipo de paquete y el rol del usuario**, no por ese modo.

---

## 2. Tipos de paquetes

| Tipo (`tipo`) | Origen | Destino | Estado de implementación |
|---|---|---|---|
| `EXPORTACION_UEB` | UEB | Dirección de Producción (`CENTRAL`) | **Implementado** (CU-08). |
| `CONFIRMACION` | Dirección de Producción | UEB de origen (`UEB:<uuid>`) | **Implementado** (CU-09 + CU-08 para recibir). |
| `CONFLICTOS` | Dirección de Producción | UEB de origen | **Formato definido**; se genera bajo petición desde el historial de conflictos. No resuelve nada. |
| `DISTRIBUCION_CENTRAL` | Dirección de Producción | UEB | **Solo diseñado**. No se implementa porque no está definido qué datos centrales debe recibir cada UEB. |
| `RECHAZO` | Dirección de Producción | UEB de origen | Implementado como `CONFIRMACION` con `estado=RECHAZADO` y detalle; evita un quinto formato. |

No hay sincronización bidireccional general: la UEB envía operación; la central devuelve solo confirmación/conflictos. Cualquier distribución de catálogos o históricos oficiales queda pendiente de aprobación.

---

## 3. Identificación y metadatos del paquete

`MANIFEST.json` (UTF-8, sin BOM):

```json
{
  "formato": "PICAN-SYNC",
  "versionProtocolo": "1.0",
  "versionEsquema": "V1+V2",
  "idPaquete": "<UUID>",
  "tipo": "EXPORTACION_UEB",
  "idUebOrigen": "<UUID o null>",
  "idDestino": "CENTRAL",
  "generadoEn": "2026-11-20T09:00:00",
  "idLote": "<UUID>",
  "cantidadRegistros": "12",
  "payload": "REGISTROS.json",
  "payloadSha256": "<64 hex>",
  "generadoPor": "<UUID de usuario o null>",
  "observaciones": ""
}
```

- `idLote` se escribe en `id_lote_sincronizacion` de los registros **en el destino**; permite trazar el paquete sin duplicar columnas en el origen.
- `payloadSha256` se calcula sobre los bytes exactos del archivo interno, con `SHA-256` y comparación `MessageDigest.isEqual`. **Es integridad, no autenticación**: no firma al emisor. Si se requiere autenticidad, hace falta firma digital y gestión de claves (pendiente).
- `generadoPor` es `UUID` de usuario de la sesión, nunca contraseña ni hash.

`REGISTROS.json`:

```json
{
  "registros": [
    {
      "tabla": "vale_entrega",
      "accion": "UPSERT",
      "idRegistro": "<UUID>",
      "version": "2",
      "idUebOrigen": "<UUID>",
      "creadoEn": "2026-03-13T09:00:00",
      "actualizadoEn": "2026-03-13T09:05:00",
      "eliminadoLogico": false,
      "datos": { "id_vale": "<UUID>", "numero_vale": "...", "cantidad_despachada": "8.500", ... }
    }
  ]
}
```

Todos los valores de `datos` se transportan como **cadenas** (o `null`) para conservar decimales y fechas sin depender de un parser JSON numérico. El importador aplica el tipo según el registro de columnas del motor. Lo mismo aplica a `cantidadRegistros`, `version`, `versionRecibida` y `versionLocal`: se escriben y leen como cadenas, nunca como números JSON.

`CONFIRMACION.json` (paquetes `CONFIRMACION`):

```json
{
  "idPaqueteOrigen": "<UUID del paquete confirmado>",
  "idLote": "<UUID>",
  "estado": "IMPORTADO|RECHAZADO|PARCIAL",
  "recibidoEn": "...",
  "procesados": [{"tabla":"...","idRegistro":"...","estado":"APLICADO|OMITIDO|RECHAZADO|CONFLICTO","detalle":""}],
  "conflictos": [{"tabla":"...","idRegistro":"...","versionRecibida":"2","versionLocal":"3"}],
  "mensaje": ""
}
```

---

## 4. Formato de archivo y seguridad

- Contenedor: ZIP (`java.util.zip`), con entradas **whitelist**:
  - `EXPORTACION_UEB`: `MANIFEST.json`, `REGISTROS.json`.
  - `CONFIRMACION`/`CONFLICTOS`/`RECHAZO`: `MANIFEST.json`, `CONFIRMACION.json`.
- Se rechaza: número de entradas distinto, nombre no reconocido, ruta con `/`, `\`, `..`, nombre absoluto, entrada duplicada, tamaño total > 20 MB, más de 5.000 registros, JSON inválido, versión de protocolo distinta de `1.0`, `payloadSha256` ausente o distinto, tipo de paquete desconocido, `idDestino` que no corresponda a la instancia receptora.
- **No se extrae ningún archivo al disco**: las entradas se leen en memoria. Por tanto no existe vector Zip Slip; aun así se validan los nombres. No se usa `ObjectInputStream`, `XMLDecoder` ni deserialización de objetos Java.
- Archivos temporales: se crean con `Files.createTempFile` dentro del directorio temporal del sistema y se eliminan en `finally`. La escritura final se hace en la ruta elegida por el usuario, con validación de directorio existente y nombre sin caracteres de control.
- El hash **no** es credencial: no sustituye firma, ni cifrado. Los USB pueden perderse: el contenido es datos operativos, sin contraseñas.

---

## 5. Reglas de selección y contenido

### 5.1 Tablas transportadas en `EXPORTACION_UEB`

| Grupo | Tablas | Tratamiento |
|---|---|---|
| Dependencias de catálogo | `ueb`, `nave`, `unidad_asociada`, `producto`, `destino`, `producto_destino` | Se exportan como contexto necesario para reconstructir FKs. **No se declara autoridad de escritura**; en destino, si la fila no existe se inserta, si existe con otra versión se registra conflicto y no se sobrescribe. |
| Operación UEB | `parte_nave`, `parte_unidad_asociada`, `produccion_carne`, `consolidado_ueb`, `vale_entrega`, `recepcion`, `incidencia` | Se exportan con todos sus metadatos. Incluye bajas lógicas. |
| Nunca transportadas | `rol`, `usuario`, `indicador_historico`, `meta_estrategica`, `registro_analisis`, `comparacion_meta`, `informe`, `fuente_informe`, `decision_directivo`, `destinatario_institucional`, `entrega_informe` | Seguridad, históricos oficiales, derivadas e informes (flujo central→UEB pendiente). Los `id_usuario` de operaciones se envían solo como referencia opcional; si no existen en destino, la fila se rechaza por dependencia. |

### 5.2 Pendientes

Se exporta una fila cuando:

1. `sincronizado_en IS NULL` (nunca confirmada), **o**
2. `version > version_confirmada` registrada en `sync_exportacion` para esa `(tabla, id_registro)`, **o**
3. `eliminado_logico = TRUE` (tombstone) y aún no confirmado.

Además se incluyen bajas de catálogo representadas hoy como `activa = FALSE`. En destino se aplican ambos marcadores según la columna existente: `activa = FALSE` y `eliminado_logico = TRUE`. **No se borra físicamente ninguna fila.**

Crear el archivo **no** marca `sincronizado_en`: solo se marca al importar una `CONFIRMACION` satisfactoria. El registro de exportación queda como `GENERADO` hasta entonces.

### 5.3 Orden de importación

`ueb` → `nave`/`unidad_asociada`/`producto`/`destino` → `producto_destino` → `parte_nave`/`parte_unidad_asociada`/`produccion_carne`/`consolidado_ueb`/`vale_entrega` → `recepcion` → `incidencia`.

Si falta una dependencia, el registro se rechaza con detalle y **no se aplica ninguna parte** de la operación atómica. Los paquetes con conflictos conservan evidencia pero no se declaran "resueltos".

---

## 6. Control persistente (migración V2)

Se crean cuatro tablas nuevas; **V1 no se modifica**:

| Tabla | Clave | Finalidad |
|---|---|---|
| `sync_paquete` | `id_paquete` | Tipo, UEB origen, destino, generación, lote, hash, estado (`GENERADO`, `RECIBIDO`, `VALIDADO`, `IMPORTADO`, `PARCIAL`, `RECHAZADO`, `CONFIRMADO`), fecha de procesamiento y mensaje. |
| `sync_item` | `(id_paquete, tabla, id_registro)` | Resultado por registro: `APLICADO`, `OMITIDO`, `RECHAZADO`, `CONFLICTO`, `CONFIRMADO`; versión y detalle. Soporta idempotencia e informe de repetidos. |
| `sync_conflicto` | `id_conflicto` | Evidencia de ambas versiones (payload recibido y local), versión de cada una, paquete de origen y estado `PENDIENTE` (nunca `RESUELTO` por el motor). |
| `sync_exportacion` | `(tabla, id_registro)` | Última versión confirmada y fecha de confirmación; evita reexportar lo ya aceptado. |

Compatibilidad: en base nueva, V1+V2 se aplican en orden; en base que ya tenía V1, el runner omite V1 por checksum y aplica solo V2. No se reescriben datos existentes.

---

## 7. Flujo y responsabilidades

| Paso | Rol (DCUS) | Servicio | Acción |
|---|---|---|---|
| Generar y exportar paquete | `JEFE_DE_UEB` (CU-08) | `SyncService.generarExportacion` | Selecciona pendientes, calcula hash, escribe ZIP, registra `GENERADO`. |
| Copiar a USB y transportar | Usuario de UEB | Manual | Fuera del sistema. |
| Recibir y validar | `ESPECIALISTA_DE_ESTADISTICA` (CU-09) | `SyncService.validarPaquete` | Estructura, versión, hash, origen/destino, duplicado. |
| Importar | `ESPECIALISTA_DE_ESTADISTICA` (CU-09) | `SyncService.importarPaquete` | Transacción, dependencias, conflictos, resultados. |
| Generar confirmación | `ESPECIALISTA_DE_ESTADISTICA` (CU-09) | `SyncService.generarConfirmacion` | ZIP con `CONFIRMACION.json`. |
| Importar confirmación en UEB | `JEFE_DE_UEB` (CU-08) | `SyncService.importarConfirmacion` | Marca `sincronizado_en`, `id_lote_sincronizacion` y estados `CONFIRMADO`. |
| Consultar historial/conflictos | Jefe (su UEB) y estadística (central) | `SyncService.consultarHistorial` / `consultarConflictos` | Solo lectura. |

La autorización se comprueba **dentro del servicio** antes de cualquier lectura/escritura; la UI filtra además las acciones por rol. Ni el Administrador ni Especialista de Producción pueden generar, importar ni confirmar paquetes. El paquete nunca modifica usuarios, roles, contraseñas ni privilegios.

---

## 8. Decisiones pendientes (no implementadas)

1. **Autoridad de escritura por tabla** (las 24 tablas; ver matriz de Fase 0).
2. **Resolución de conflictos**: no se decide automáticamente; se conservan ambas evidencias y se deja `PENDIENTE`.
3. **Dirección y contenido de distribución central→UEB** (`DISTRIBUCION_CENTRAL`).
4. **Relación entre 28 unidades asociadas y 25 UEB**.
5. **Frecuencia de intercambio** y ventanas de corte (aquí se exporta todo lo pendiente; no se fija periodicidad).
6. **Reglas para modificaciones simultáneas** y para cambios de catálogos en central vs UEB más allá de la detección por versión.
7. **Autenticidad del emisor**: requiere firma digital e infraestructura de claves; SHA-256 solo cubre integridad.
8. **Permisos internos finos** por CU y la relación usuario↔UEB que permitiría limitar al Jefe a "su UEB".
9. **Reglas para registros huérfanos** cuyo `id_usuario` no existe en la central (el motor los rechaza por dependencia; no inventa un usuario sustituto).

---

## 9. Límites de esta versión

- Sin Internet, VPN, replicación ni conexión directa entre bases.
- Sin resolución automática de conflictos.
- Sin sincronización de seguridad, históricos oficiales ni informes.
- Sin CU-18, CU-19 ni CU-04.
- Sin GUI que sugiera sincronización automática: todos los pasos son manuales por archivo.

---

## 10. Verificación de la implementación

- Motor: `src/cu/pican/sync/` (ZIP + JSON sobre JDK, SHA-256, historial, conflictos y control de exportación).
- Migración: `sql/migrations/V2__sincronizacion.sql`; V1 permanece intacta.
- Integración: `AppContext.getSyncService()` solo en PostgreSQL; en MEMORY lanza `SyncException` sin fallback.
- Interfaz: `SyncModulePanel`, `PermisosInterfaz`, `SideMenuPanel`, `MainFrame` y `NavigationController`.
- Arnés: `test/cu/pican/verify/SincronizacionPostgresTest.java` → **14/14** con UEB A, UEB B y central, incluyendo corrupción, versión incompatible, destino incorrecto, Zip Slip, incompletos, conflictos, idempotencia, autorización por rol, confirmación y rechazo en MEMORY.
- Evidencias: `test-results/sincronizacion-4.15.log` y `RESULTADOS_SINCRONIZACION_4.15.txt`.
- Detalles de ejecución y regresión: `PRUEBAS_ETAPA_4.15.md`.

