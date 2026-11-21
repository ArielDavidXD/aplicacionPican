# PROGRESO — ETAPA 4.15 (PICAN · Validación integral y sincronización UEB↔central)

Fuente de verdad: `C:\Users\Ariel David\Desktop\bioproductos\aplicacionPican\`
Fecha de inicio: 2026-11-20.

> Registro vivo. Se actualiza al terminar cada fase. No sustituye a la implementación.

---

## Estado general

- **Fase actual:** Fase 8 — Informe final y cierre (completada).
- **Fases completadas:** 0, 1, 2, 3, 4, 5, 6, 7 y 8.
- **Estado previo verificado en disco:** informes 4.14 presentes y coherentes (35/35 arneses, 14/14 demos MEMORY, 2/2 demos PostgreSQL, 174 fuentes).
- **Git:** raíz `C:/Users/Ariel David/Desktop/bioproductos`, HEAD `33bdf0d`; `aplicacionPican/` sin seguimiento. Sin restaurar/descartar/sobrescribir cambios.
- **Copia de seguridad:** `C:\Users\Ariel David\AppData\Local\Temp\opencode\pican-etapa-4.15-backup-20261120-083718`.
- **Entorno verificado:** clúster temporal PostgreSQL 17.6 en `127.0.0.1:55432`, usuario `pican_test`, auth `trust`. Bases existentes: `pican_appcontext_test`, `pican_funcional_test`, `pican_funcional2_test`, `pican_integracion_test`, `pican_servicios_test`, `pican_tx_test`, `pican_usuarios_test`. No se tocó el servicio 5432.
- **Compilación oficial:** `.\scripts\compilar.ps1` → **OK: 182 fuentes compiladas en `out`**; `.\scripts\ejecutar.ps1 -Modo memoria|postgresql`.
- **Librerías:** solo `lib/postgresql-42.7.13.jar`. No hay librería JSON; el motor usa ZIP + JSON propio sobre JDK (`java.util.zip`, `java.security.MessageDigest`).
- **Bases de sincronización:** `pican_sync_ueb_a_test`, `pican_sync_ueb_b_test`, `pican_sync_central_test`; regresión en `pican_regresion_415_test`. Todas con `test` en el nombre y recreadas antes de ejecutar.
- **Evidencias:** `test-results/integral-4.15.log`, `test-results/sincronizacion-4.15.log`, `test-results/permisos-interfaz-4.15.log`, `test-results/regresion-*.log`, `test-results/demo-memoria-*.log` y `test-results/RESULTADOS_*.txt` (sin contraseñas).

---

## Fase 0 — Inspección y planificación (COMPLETADA)

### Hallazgos principales

1. V1 tiene **24 tablas** y las 7 columnas de metadatos (`id_ueb_origen`, `version`, `creado_en`, `actualizado_en`, `sincronizado_en`, `id_lote_sincronizacion`, `eliminado_logico`) en **todas**; 24 índices de sincronización y 24 triggers de `actualizado_en`. Java **nunca** escribe `id_ueb_origen`, `sincronizado_en` ni `id_lote_sincronizacion`; `eliminado_logico` solo se filtra en lectura (`JdbcRepositorySupport:39-41`).
2. `deleteById()` de los repositorios JDBC es **borrado físico** (`JdbcRepositorySupport:123-138`). La sincronización no usará ese método: aplicará bajas lógicas por SQL controlado.
3. No existe `SyncService`, modelo de paquete, historial ni lote. Solo el cascarón Swing `SyncModulePanel` con filas ficticias.
4. Autenticación real (PBKDF2, sesión única en `AuthService`); **no hay autorización por rol en servicios** salvo invariantes internas de `UsuarioService`. La UI muestra los 10 módulos a cualquier sesión.
5. No existe relación usuario↔UEB (`Usuario.java:12` lo declara pendiente). No se inventa.
6. `informe.id_informe_raiz` es FK obligatoria a sí misma, no deferible: exige orden raíz→revisiones.
7. Autoridad de escritura UEB vs central: **PENDIENTE en todas las tablas**. Se transportan dependencias como referencia y se aplican reglas técnicas de integridad (insertar si falta, conflicto si difiere), sin decidir propiedad empresarial.

### Matriz de tablas (Fase 0)

Leyenda: **Sí\*** = se sincroniza en flujo UEB→central con regla técnica de dependencia/conflicto, sin declarar autoridad de escritura. **No** = queda fuera del paquete UEB→central.

| Tabla | Tipo de información | Origen esperado | Destino esperado | ¿Sincronizar? | Dependencias | Observaciones |
|---|---|---|---|---|---|---|
| `ueb` | Catálogo / organización | Central (pendiente) | Referencia en UEB y central | Sí\* | — | Identifica `id_ueb_origen`; autoridad pendiente. |
| `nave` | Catálogo operativo | UEB (pendiente) | Central | Sí\* | `ueb` | Debe preceder a `parte_nave`. |
| `unidad_asociada` | Catálogo (28 unidades sin PC) | UEB que transcribe (pendiente) | Central | Sí\* | — | **No** se sincroniza directamente con las unidades; vínculo UEB pendiente. |
| `producto` | Catálogo operativo | Central (pendiente) | UEB y central | Sí\* | — | Referencia de vales. |
| `destino` | Catálogo operativo | Central (pendiente) | UEB y central | Sí\* | — | Referencia de vales e informes. |
| `producto_destino` | Catálogo / relación | Central (pendiente) | UEB y central | Sí\* | `producto`, `destino` | Valida vales. |
| `rol` | Seguridad | Central | UEB | **No** | — | Regla explícita: paquetes nunca modifican roles. |
| `usuario` | Seguridad / autenticación | Central | UEB | **No** | `rol` | Sin contraseñas ni cuentas en paquetes. |
| `parte_nave` | Operativa UEB | UEB | Central | Sí | `nave`, `usuario` (nullable) | Incluye bajas lógicas. |
| `parte_unidad_asociada` | Operativa UEB (papel transcrito) | UEB | Central | Sí | `unidad_asociada`, `usuario` | Sin sincronización con la unidad asociada. |
| `produccion_carne` | Operativa UEB | UEB | Central | Sí | `ueb`, `usuario` | Incluye bajas lógicas. |
| `consolidado_ueb` | Derivada/consolidada UEB | UEB | Central | Sí | `ueb`, `usuario` | Estado parcial se transporta tal cual. |
| `vale_entrega` | Operativa UEB | UEB | Central | Sí | `ueb`, `destino`, `producto`, `usuario` | `numero_vale` UNIQUE global. |
| `recepcion` | Operativa UEB | UEB | Central | Sí | `vale_entrega`, `usuario` | Un vale → una recepción. |
| `incidencia` | Operativa UEB | UEB | Central | Sí | `vale_entrega`, `recepcion`, `usuario` | Requiere vale y recepción previos. |
| `indicador_historico` | Histórica oficial | Dirección | Solo lectura en UEB | **No** | — | Carga idempotente local; no se exporta. |
| `meta_estrategica` | Histórica oficial | Dirección | Solo lectura en UEB | **No** | — | Igual que indicadores. |
| `registro_analisis` | Derivada / apoyo | Central | — | **No** | — | Sin repositorio; se deriva de datos operativos. |
| `comparacion_meta` | Derivada / apoyo | Central | — | **No** | — | Sin repositorio. |
| `informe` | Informe CU-15 | Central | UEB (distribución pendiente) | **No** | `informe` (raíz), `usuario` | No se exporta desde UEB; flujo central→UEB sin aprobar. |
| `fuente_informe` | Detalle de informe | Central | — | **No** | `informe` | ídem. |
| `decision_directivo` | Decisión registrada por estadística | Central | — | **No** | `informe` | Directivo no tiene cuenta. |
| `destinatario_institucional` | Catálogo sembrado (GEGAN/MINAG) | Central | UEB | **No** | — | Semilla V1; no se transporta. |
| `entrega_informe` | Detalle de informe | Central | — | **No** | `informe`, `destinatario_institucional` | ídem. |

**Orden de importación:** `ueb` → (`nave`, `unidad_asociada`, `producto`, `destino`) → `producto_destino` → (`parte_nave`, `parte_unidad_asociada`, `produccion_carne`, `consolidado_ueb`, `vale_entrega`) → `recepcion` → `incidencia`. `rol`/`usuario` nunca se importan; `usuario` se trata como referencia opcional (si falta, el registro se rechaza por dependencia y se documenta).

### Entregables de Fase 0

- Este archivo con diagnóstico y matriz.
- `MATRIZ_PERMISOS_PICAN.md` con actores, permisos reales y pendientes.

---

## Fase 1 — Validación integral PostgreSQL (COMPLETADA)

Objetivo: integración AppContext → servicios → repositorios JDBC → transacciones → PostgreSQL, sin repetir 145 checks JDBC ni las pruebas transaccionales previas.

**Resultado: 20/20 comprobaciones aprobadas.**

| Ámbito | Casos | Resultado |
|---|---|---|
| Arranque, migraciones y login | INT-01, INT-02 | 2/2 aprobadas |
| Catálogos (UEB, naves, unidades, productos, destinos, producto-destino) | INT-03…INT-08 | 6/6 aprobadas |
| Producción y consolidación | INT-09…INT-11 | 3/3 aprobadas |
| Vales, recepciones, incidencias y anulaciones | INT-12…INT-15 | 4/4 aprobadas |
| Análisis, informes y usuarios | INT-16…INT-18 | 3/3 aprobadas |
| Persistencia tras reconstruir `AppContext` y metadatos | INT-19, INT-20 | 2/2 aprobadas |

### Ejecución real

- Base temporal exclusiva: `pican_integracion_415_test` en el clúster `127.0.0.1:55432` (recreada antes de la prueba).
- Arnés: `test/cu/pican/verify/ValidacionIntegralPostgresTest.java`.
- Compilación: `javac -encoding UTF-8 -cp "lib\*" -d out-test <fuentes>` → 179 fuentes.
- Comando: `java -Dpican.test.url=... -Dpican.test.user=pican_test -Dpican.test.migrations=sql/migrations -cp "out-test;lib\*" cu.pican.verify.ValidacionIntegralPostgresTest`.
- Evidencia: `test-results/integral-4.15.log` y `test-results/RESULTADOS_INTEGRAL_4.15.txt` (sin contraseñas).
- Error corregido durante la fase: el helper SQL comparaba un `UUID` contra `VARCHAR`; se ajustó el parámetro con `Types.OTHER`. También se precisó que la baja lógica actual de catálogos usa `activa=false` (`eliminado_logico` sigue disponible para marcas de sincronización; los servicios no lo escriben hoy).

### Interfaces

La lista de pruebas manuales GUI quedó en `PRUEBAS_MANUALES_GUI_4.15.md`. **No se ejecutaron manualmente**; no se afirma lo contrario.

---

## Fase 2 — Protocolo y migración V2 (COMPLETADA)

- `DISEÑO_SINCRONIZACION_PICAN.md`: protocolo `PICAN-SYNC 1.0`, esquema `V1+V2`, tipos `EXPORTACION_UEB`, `CONFIRMACION`, `CONFLICTOS`, `RECHAZO`; distribución central→UEB solo diseñada.
- `sql/migrations/V2__sincronizacion.sql`: `sync_paquete`, `sync_item`, `sync_conflicto`, `sync_exportacion`. V1 intacta.
- Decisiones de seguridad: ZIP + JSON, SHA-256 como integridad, sin `ObjectInputStream`, sin credenciales en paquetes.

---

## Fase 3 — Motor de sincronización (COMPLETADA)

- `src/cu/pican/sync/`: `SyncException`, `SyncJson`, `SyncModelo`, `SyncArchivo`, `SyncEsquema`, `SyncRepositorio`, `SyncService`.
- Reglas: dependencias insertadas si faltan, `id_usuario` ausente rechaza la fila, versiones, conflictos, idempotencia y confirmación.
- La UI no decide reglas: solo invoca servicios.

---

## Fase 4 — Integración AppContext e interfaz (COMPLETADA)

- `AppContext.getSyncService()` solo para PostgreSQL; MEMORY lanza `SyncException` sin simular sincronización.
- `PermisosInterfaz` centraliza la matriz de módulos por rol; `SideMenuPanel` filtra el menú y `NavigationController` bloquea navegación directa no autorizada.
- `SyncModulePanel` reemplazó el simulacro por acciones reales CU-08/CU-09 y vistas de historial/conflictos.
- Verificación: `PermisosInterfazTest` **6/6**.

---

## Fase 5 — Pruebas exhaustivas de sincronización (COMPLETADA)

- Arnés `SincronizacionPostgresTest` con UEB A, UEB B y central: **14/14 aprobadas**.
- Cubre: generación, validación, importación, idempotencia, autorización por servicio, corrupción SHA-256, versión incompatible, destino incorrecto, Zip Slip, incompletos, conflictos, dos UEB sin mezclar orígenes, confirmación y rechazo en MEMORY.
- Correcciones técnicas justificadas: normalización ISO de fechas, doble `executeUpdate()` eliminado y cierre de confirmación con ítems `GENERADO`.
- Evidencia: `test-results/sincronizacion-4.15.log` y `RESULTADOS_SINCRONIZACION_4.15.txt`.

---

## Fase 6 — Regresión (COMPLETADA)

| Arnés | Resultado | Justificación |
|---|---|---|
| `AppContextIntegrationTest` | 12/12 | Cambió la construcción del contexto. |
| `ServiciosTransaccionalesTest` | 5/5 | Cableado de servicios. |
| `FuncionalPostgresTest` | 8/8 | Reconstrucción de contexto e informes. |
| `TransaccionesPostgresTest` | 10/10 | Nuevo repositorio usa el mismo manejador transaccional. |
| 14 demos MEMORY | 14/14 | El modo MEMORY sigue operativo. |

No se repitieron los 145 checks JDBC ni la validación V1 porque V1 no fue modificada.

---

## Fase 7 — Documentación operativa (COMPLETADA)

- `MANUAL_SINCRONIZACION_PICAN.md`: procedimiento por rol, USB, formato, rechazos y resolución de problemas.
- `PRUEBAS_ETAPA_4.15.md`: evidencias consolidadas.
- `MATRIZ_PERMISOS_PICAN.md` y `DISEÑO_SINCRONIZACION_PICAN.md` actualizados con el estado real.
- `PRUEBAS_MANUALES_GUI_4.15.md`: lista preparada, **no ejecutada**.

---

## Fase 8 — Informe final y cierre (COMPLETADA)

- `INFORME_FINAL_ETAPA_4.15.md` con alcance, decisiones, verificación, pendientes y control de cambios.
- Compilación oficial final: **OK: 182 fuentes compiladas en `out`**.
- Git: sin commits, sin push y sin cambios de historial; `aplicacionPican/` continúa sin seguimiento.
- Seguridad: no se declara completa fuera del módulo de sincronización; los permisos pendientes quedan documentados.
