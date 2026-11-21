# MANUAL DE SINCRONIZACIÓN UEB ↔ DIRECCIÓN DE PRODUCCIÓN — PICAN

Etapa 4.15 · Protocolo `PICAN-SYNC` versión `1.0` · Esquema `V1+V2`.

> Alcance: intercambio **manual** de archivos transportados en memoria USB. No hay Internet, VPN, replicación PostgreSQL ni conexión permanente entre las 25 UEB y la central. Las 28 unidades no estatales no participan: entregan papel y la UEB transcribe.

---

## 1. Requisitos antes de empezar

| Requisito | Verificación |
|---|---|
| Base PostgreSQL local (UEB o central) | La aplicación debe iniciar en modo `postgresql`. El modo `MEMORY` **rechaza** la sincronización con un mensaje explícito. |
| Migración V2 aplicada | `sql/migrations/V2__sincronizacion.sql` crea `sync_paquete`, `sync_item`, `sync_conflicto` y `sync_exportacion`. V1 no se modifica. |
| Usuario con rol correcto | UEB: `JEFE_DE_UEB`. Central: `ESPECIALISTA_DE_ESTADISTICA`. Ni `ADMINISTRADOR` ni `ESPECIALISTA_DE_PRODUCCION` pueden sincronizar. |
| Memoria USB disponible | Se recomienda una carpeta por UEB y otra para las confirmaciones devueltas. |
|Sin contraseñas en el archivo | El ZIP contiene solo datos operativos, metadatos y `id_usuario` como referencia. Nunca contraseñas, hashes, roles ni privilegios. |

La autorización se comprueba **dentro de `SyncService`**, antes de leer o escribir. El menú y los botones filtran además por rol, pero no son la única barrera.

---

## 2. Roles y operaciones permitidas

| Operación | JEFE_DE_UEB | ESPECIALISTA_DE_ESTADISTICA | ADMINISTRADOR / ESP. PRODUCCIÓN |
|---|---|---|---|
| Preparar paquete de UEB (CU-08) | Sí | No | No |
| Validar paquete recibido (CU-09) | No | Sí | No |
| Importar paquete en central (CU-09) | No | Sí | No |
| Generar confirmación | No | Sí | No |
| Importar confirmación en UEB (CU-08) | Sí | No | No |
| Consultar historial y conflictos | Sí | Sí | No |

---

## 3. Flujo completo (ida y vuelta)

### Paso A — En la UEB (Jefe de UEB)

1. Iniciar sesión con un usuario `JEFE_DE_UEB`.
2. Abrir **Sincronización mediante USB**.
3. Pulsar **Preparar paquete**.
4. Seleccionar la **UEB de origen** de la lista. No existe relación usuario↔UEB, por eso se pide explícitamente.
5. Elegir como destino un archivo `.zip` dentro de la memoria USB, por ejemplo `SYNCA-paquete-20261120.zip`.
6. Confirmar el mensaje con el identificador del paquete. Entregar la USB a la Dirección de Producción.

**Importante:** crear el ZIP **no** marca los registros como sincronizados. Solo se marcan cuando la confirmación vuelva y se importe correctamente.

### Paso B — En la central (Especialista de Estadística)

1. Iniciar sesión con un usuario `ESPECIALISTA_DE_ESTADISTICA`.
2. Abrir **Sincronización mediante USB**.
3. Pulsar **Validar paquete** y seleccionar el ZIP de la USB. Revisar: identificador, tipo, origen, destino `CENTRAL`, cantidad de registros e integridad SHA-256.
4. Pulsar **Importar paquete**. El destino de la importación es `CENTRAL`.
5. Si el estado es `IMPORTADO`, el paquete se aplicó íntegramente. Si es `PARCIAL`, abrir la pestaña **Conflictos**: cada fila queda `PENDIENTE` con ambas versiones; **no se resuelve automáticamente**.
6. Pulsar **Generar confirmación**, elegir el paquete recibido y guardar el ZIP de confirmación en la USB.
7. Devolver la USB a la UEB de origen.

### Paso C — De vuelta en la UEB (Jefe de UEB)

1. Iniciar sesión como `JEFE_DE_UEB`.
2. Abrir **Sincronización mediante USB**.
3. Pulsar **Importar confirmación** y seleccionar el ZIP devuelto.
4. El sistema marca `sincronizado_en` y `id_lote_sincronizacion` en los registros confirmados y cierra el lote en `sync_exportacion`.
5. Pulsar **Actualizar historial** para comprobar el estado `CONFIRMADO`.

Si la confirmación indica `PARCIAL`, los registros aplicados se cierran y los conflictos siguen pendientes de decisión humana. No se borra la evidencia.

---

## 4. Formato del archivo (para soporte técnico)

Contenedor ZIP con dos entradas como máximo:

| Tipo de paquete | Entradas permitidas |
|---|---|
| `EXPORTACION_UEB` | `MANIFEST.json`, `REGISTROS.json` |
| `CONFIRMACION` / `RECHAZO` / `CONFLICTOS` | `MANIFEST.json`, `CONFIRMACION.json` |

`MANIFEST.json` incluye `formato`, `versionProtocolo=1.0`, `versionEsquema=V1+V2`, `idPaquete`, `tipo`, `idUebOrigen`, `idDestino`, `generadoEn`, `idLote`, `cantidadRegistros`, `payload`, `payloadSha256`, `generadoPor` y `observaciones`.

`REGISTROS.json` transporta una lista de registros con `tabla`, `accion`, `idRegistro`, `version`, `idUebOrigen`, metadatos y `datos` como cadenas. El orden de importación respeta dependencias: catálogos → relaciones → operaciones → recepciones → incidencias.

**No se usa `ObjectInputStream` ni deserialización de objetos Java.** No se extraen archivos al disco: se leen en memoria y se validan nombres, número de entradas, tamaño y rutas.

---

## 5. Reglas de integridad y rechazo

El motor rechaza automáticamente:

- SHA-256 distinto (archivo corrupto o modificado).
- Versión de protocolo distinta de `1.0`.
- `idDestino` que no corresponda a la instancia receptora.
- Entradas ZIP no permitidas, rutas con `/`, `\` o `..`, entradas duplicadas o archivos incompletos.
- JSON inválido, más de 5.000 registros o más de 20 MB.
- Dependencias ausentes: si un `id_usuario` referenciado no existe en la central, la fila se rechaza; no se crea un usuario sustituto.
- Paquete repetido: se omite sin duplicar filas (idempotencia).

**Conflicto:** versión menor, o versión igual con contenido distinto. Se conserva `sync_conflicto` en estado `PENDIENTE`; la versión local no se sobrescribe. Versión mayor se aplica salvo que exista conflicto pendiente. No existe "el último generado gana".

**Bajas:** se aplican como `activa=false` y/o `eliminado_logico=true`. Nunca se ejecuta borrado físico desde la sincronización.

---

## 6. Seguridad y límites

- SHA-256 es **integridad**, no autenticación: demuestra que el archivo no cambió, no quién lo generó. Si se requiere autenticidad del emisor, hace falta firma digital y gestión de claves (pendiente).
- Un USB puede extraviarse: no guardar credenciales en él. El paquete no contiene contraseñas ni información de roles.
- No se sincronizan `rol`, `usuario`, históricos oficiales (`indicador_historico`, `meta_estrategica`), informes ni tablas derivadas.
- No se inventan reglas empresariales: autoridad de escritura por tabla, resolución de conflictos, frecuencia de intercambio y distribución central→UEB siguen pendientes.
- Fuera de alcance: CU-18, CU-19, CU-04, Internet, replicación directa y sincronización en tiempo real.

---

## 7. Resolución de problemas

| Síntoma | Causa probable | Acción |
|---|---|---|
| "La sincronización por archivos requiere PostgreSQL" | Se está usando MEMORY | Reiniciar con PostgreSQL y V2 aplicada. |
| "No está autorizado para..." | Rol incorrecto | Cambiar a un usuario con el rol indicado en §2. |
| "Integridad del paquete inválida" | USB con errores o archivo editado | Copiar el ZIP nuevamente y validar otra vez. |
| "Versión de protocolo incompatible" | Paquete de otra versión | No importar; solicitar un paquete generado con protocolo `1.0`. |
| "Destino incorrecto" | Paquete de otra UEB o para otra instancia | Verificar la etiqueta de la USB y repetir la copia. |
| "Paquete ya procesado / OMITIDO" | Reimportación del mismo ZIP | No es un error: se omite por idempotencia. |
| "CONFLICTO" en la pestaña de conflictos | Versión local distinta | No pulsar ninguna acción automática; escalar la decisión según el procedimiento empresarial que se defina. |
| "Dependencia no encontrada" | Falta catálogo o usuario de referencia | Verificar que el paquete incluya las dependencias; no crear datos ficticios. |

---

## 8. Verificación técnica

Los pasos GUI de esta etapa quedaron registrados como **no ejecutados** en `PRUEBAS_MANUALES_GUI_4.15.md`. La verificación automatizada real está en `PRUEBAS_ETAPA_4.15.md` y sus evidencias en `test-results/`.
