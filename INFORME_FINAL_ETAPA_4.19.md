# INFORME FINAL — ETAPA 4.19

**Estado general: APROBADO CON OBSERVACIONES.**  
Fecha: 2026-11-20 · Proyecto: `aplicacionPican` · Alcance: revisión integral, estabilización, integración y validación final de CU-01 a CU-19.

## 1. Objetivo y límites

Se revisó el sistema completo sin agregar funcionalidades ni rehacer las etapas 4.14 a 4.18. La Etapa 4.19 se ejecutó en tres bloques:

1. **Fase 0 — inventario y diagnóstico**, cerrada antes de tocar código (`DIAGNOSTICO_ETAPA_4.19_FASE_0.md`).
2. **Corrección demostrable**, solo con prueba ejecutada.
3. **Documentación final y clasificación honesta** de lo que no pudo resolverse dentro del alcance.

Restricciones cumplidas:

- No se modificó nada fuera de `aplicacionPican/`.
- No se hicieron commits, push, reset ni cambios de estado Git. `HEAD` sigue en `33bdf0d13d9fefe9cdfb36c5377e15e1db9ed0a6`.
- No se ejecutaron operaciones destructivas sobre bases reales. Todas las pruebas PostgreSQL usaron bases temporales con `test` en el nombre, creadas y eliminadas por los arneses.
- No se cambiaron PICAN-SYNC 1.0 ni las migraciones V1, V2 o V3.
- No se inventaron reglas de negocio, roles, relación usuario↔UEB, indicadores ni permisos.
- Las contraseñas se entregaron solo por `-D` o variables de entorno; los documentos usan los placeholders `<CLAVE_PG_PRUEBA>`, `<CLAVE_ADMIN_PRUEBA>` y `<CLAVE_APP_PRUEBA>`.

## 2. Hallazgos corregidos y evidencia

| ID | Corrección | Evidencia ejecutada |
|---|---|---|
| D-01 | `importarConfirmacion` respeta `estado=RECHAZADO`: no confirma, no escribe `sincronizado_en`, no registra `version_confirmada` y devuelve `RECHAZADO`. La ruta `IMPORTADO` sigue confirmando. | D-01.a–D-01.f, base temporal `pican_estabilidad_419_<ts>_test`. |
| D-02 | `asignarRol` y `actualizarDatos` rechazan dejar el sistema sin cuenta ADMINISTRADOR utilizable. Con un segundo administrador el cambio sí se permite. | D-02.a–D-02.d. |
| D-03 | Sin `-Dpican.admin.password`/`PICAN_ADMIN_PASSWORD`, el arranque PostgreSQL falla cerrado con instrucciones y sin imprimir credencial. MEMORY conserva la generación temporal de demostración. | D-03.a–D-03.e, captura de `System.out`. |
| D-06 | `generarConfirmacion` filtra los conflictos por `id_paquete`, en lugar de arrastrar todos los pendientes. | D-06.a–D-06.b. |
| D-07 | `registrarConflicto` devuelve el `ItemResultado` y se eliminó la segunda escritura que sobrescribía el motivo. | D-07.a–D-07.b. |
| D-13 | Textos de `AdminModulePanel` y `ReceptionModulePanel` actualizados a estado real. | Compilación 228/228. |
| D-14 | Ejemplos JSON del diseño de sincronización usan cadenas para cantidades y versiones, igual que `SyncJson`. | Revisión documental. |
| D-15 | `pican.properties.example` aclara que `pican.migrations.dir` solo se acepta como propiedad de sistema. | Revisión documental. |
| D-16 | `scripts/ejecutar.ps1` advierte con `PICAN_DB_PASSWORD`, variable que sí lee `DatabaseConfig`. | Revisión documental. |
| D-19 | Correcciones de cifras, número de logs, reutilización del identificador SIN-13, estado de 4.18 e índice de V1. | Revisión documental. |
| D-20 | Literales de credenciales de prueba sustituidos por placeholders en los documentos de 4.15, 4.16 y 4.17. | Búsqueda sin coincidencias de literales en `*.md`. |
| D-25 | `DashboardModulePanel` declara que sus indicadores son ficticios y requieren definición funcional. | Revisión documental. |
| D-26 | Copias antiguas sin seguimiento fuera del directorio permitido se mencionan, sin tocarlas. | Sección 5. |

Arnés nuevo: `test/cu/pican/verify/Estabilizacion419Test.java`. Resultado: **19/19 comprobaciones aprobadas**, sin repetir las baterías aprobadas.

## 3. Regresiones repetidas por cambio real

Se repitieron únicamente las pruebas cuyo código subyacente cambió:

| Arnés | Motivo | Resultado |
|---|---|---|
| `AutorizacionRolesTest` (MEMORY y POSTGRES) | Cambio en `UsuarioService` (D-02) | 21/21 + 21/21 |
| `AppContextIntegrationTest` | Cambio en initializer/AppContext (D-03) | 12/12 |
| `SincronizacionPostgresTest` | Cambio en `SyncService`/`SyncRepositorio` (D-01, D-06, D-07) | 14/14 |
| `CopiasSeguridadTest` (MEMORY y POSTGRES) | Arranque y contexto usados por CU-19 | 15/15 + 23/23 |
| `Estabilizacion419Test` | Nuevo | 19/19 |

**Total ejecutado en 4.19: 125 comprobaciones aprobadas** (19 nuevas + 106 de regresión afectada). El resto de la evidencia 4.14–4.18 se reutilizó sin repetir.

Compilación final: `OK: 228 fuentes compiladas en out.` y `OK: 12 arneses compilados.`

## 4. Lo que NO se corrigió y por qué

| ID | Decisión | Justificación |
|---|---|---|
| D-04 | **RECHAZADA para esta etapa** | Reforzar el cambio obligatorio en `AuthService`/`AutorizacionService` invalidaría arneses aprobados de 4.16. Queda como observación de defensa en profundidad. |
| D-08 | Desviación documentada | Cambiar `listarPendientes` altera la semántica verificada en 4.15 y requiere decisión técnica. |
| D-12 | No corregido | `PermisosInterfazTest:42-43` aprueba explícitamente el rol `null` permisivo; alinearlo implicaría retractar evidencia aprobada. |
| D-05, D-09, D-10, D-11, D-17, D-18 | Observaciones | No hay uso indebido demostrado o haría falta una decisión de diseño/seguridad fuera del alcance. |
| D-21, D-22, D-23 | BLOQUEADO | Dependen de contrato PICAN-SYNC, plantillas oficiales o decisiones empresariales. |
| D-24 | NO PROBADO | No hubo interacción real con Swing. Se entrega `PRUEBAS_MANUALES_GUI_ETAPA_4.19.md`; no se afirma ejecución. |

## 5. Clasificación final de apartados

| Apartado | Clasificación |
|---|---|
| Correcciones D-01, D-02, D-03, D-06, D-07 y sus pruebas | **APROBADO** |
| Compilación y regresiones afectadas | **APROBADO** |
| Documentación, configuración y textos corregidos | **APROBADO CON OBSERVACIONES** (literal histórico conservado en `AppContextIntegrationTest`) |
| Vulnerabilidad de credencial inicial en consola | **APROBADO** (D-03) |
| Protección de última cuenta ADMINISTRADOR | **APROBADO** (D-02) |
| SIN-13 completo (bajada central y desbloqueo) | **BLOQUEADO** |
| Validación real de interfaz Swing | **NO PROBADO** |
| Refuerzo de cambio obligatorio en capa de servicios | **RECHAZADO** para 4.19, documentado |
| Copias sin seguimiento fuera de `aplicacionPican/` | **APROBADO CON OBSERVACIONES**; no se eliminan |

## 6. Cierre

La Etapa 4.19 deja el sistema estable en los puntos demostrables: integridad de rechazos de sincronización, protección de la cuenta administrativa, ausencia de secretos en consola y coherencia documental. Las reservas restantes son externas o decisiones deliberadamente no tomadas para no romper evidencia aprobada; están enumeradas en `PENDIENTES_PICAN.md`.
