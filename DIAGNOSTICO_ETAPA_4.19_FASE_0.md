# DIAGNÓSTICO FASE 0 — ETAPA 4.19

Estado: **Fase 0 completada; cierre de correcciones registrado en la sección 4.**  
Fecha: 2026-11-20 · Alcance: únicamente `aplicacionPican/`. El cuerpo de las secciones 1 a 3 conserva el diagnóstico original previo a cualquier corrección.

## 1. Qué se inspeccionó

- Estructura completa del proyecto: `src/` (228 fuentes declaradas en 4.18), `test/` (11 arneses), `sql/migrations/` (V1, V2, V3), `scripts/`, `config/`, `lib/`, `out/`, `out-test/`, `test-out/`, `test-results/` y documentación.
- CU-01 a CU-19: `CasoUso.java`, servicios, repositorios Memory/JDBC, sincronización, importación, backup y paneles Swing.
- `MATRIZ_PERMISOS_PICAN.md`, `DISEÑO_SINCRONIZACION_PICAN.md`, `MANUAL_SINCRONIZACION_PICAN.md`, `DISENO_CU19.md`, `MANUAL_COPIAS_SEGURIDAD_PICAN.md`, `CU-17_NOTAS.md`.
- Informes finales, progresos y pruebas de las etapas 4.14 a 4.18, además de los diagnósticos Fase 0 de CU-18 y CU-19.
- SQL V1/V2/V3, `MigrationRunner`, `DatabaseConfig`, `pican.properties.example`, `compilar.ps1`, `ejecutar.ps1`.
- Evidencia previa disponible en `test-results/` (logs de 4.14, 4.15, 4.16, 4.17 y demos; 4.18 documentó salidas y limpieza en `PRUEBAS_ETAPA_4.18.md`).

### Estado Git (solo lectura)

- `HEAD = 33bdf0d13d9fefe9cdfb36c5377e15e1db9ed0a6` (`Negocio y Sistema`), sin `git diff`, sin cambios en el índice.
- `aplicacionPican/` sigue **sin seguimiento**. Preexisten además entradas sin seguimiento fuera del directorio permitido: `aplicacionPican.rar` y una copia antigua en `Modelo Caso de uso de Negocio/aplicacionPican/`. **No se tocan.**
- No se realizó ningún commit, push, reset ni cambio de estado Git.

### Evidencia reutilizable identificada

| Origen | Resultado previo | Uso en 4.19 |
|---|---|---|
| 4.14 | 35/35 arnés + 16 demos; 145/145 JDBC previos | No repetir sin cambio |
| 4.15 | 89 comprobaciones, incluidas 14/14 sincronización | Reutilizar 14/14 |
| 4.16 | 99 comprobaciones (autorización 21/21 MEM + 21/21 PG) | Reutilizar salvo cambio |
| 4.17 | 90 comprobaciones (importación 15/15 MEM + 15/15 PG) | Reutilizar |
| 4.18 | 98 comprobaciones (copias 15/15 MEM + 23/23 PG) | Reutilizar salvo cambio |

---

## 2. Clasificación de problemas

Leyenda: **CRÍTICO** impide una función esencial o compromete datos; **ALTO** error funcional/de autorización importante; **MEDIO** defecto parcial; **BAJO** documental/visual/usabilidad; **PENDIENTE EXTERNO** requiere decisión o recurso ajeno al proyecto.

| ID | Prioridad | Hallazgo (evidencia) | Impacto | Acción propuesta |
|---|---|---|---|---|
| D-01 | **CRÍTICO** | `SyncService.importarConfirmacion` (`:120-162`) lee `idPaqueteOrigen`, `idLote` y `procesados`, pero **ignora `estado`**. Una confirmación con `estado=RECHAZADO` (prevista en `DISEÑO_SINCRONIZACION_PICAN.md:26,87`) llama igualmente a `confirmarPaquete` (`SyncRepositorio:170-201`), que escribe `sincronizado_en`, lote y `version_confirmada`. | La central puede rechazar un paquete y la UEB lo marca como sincronizado; con la regla actual de pendientes no volverá a exportarse. Riesgo de pérdida de integridad de datos. | Corregir con prueba dedicada: un `RECHAZADO` no debe confirmar el paquete de origen. |
| D-02 | **ALTO** | `UsuarioService.actualizarDatos` (`:147-159`) y `asignarRol` (`:161-169`) cambian el rol sin la guarda que sí existe en `desactivar` (`:184-205`) y `eliminar` (`:208-226`). | Se puede dejar el sistema sin administrador activo incumpliendo el requisito explícito de protección de la última cuenta. | Corregir con prueba: cambiar el rol del único administrador debe fallar. |
| D-03 | **ALTO** | `UsuarioInicialInitializer` (`:29-38`) genera la contraseña inicial cuando no hay `-Dpican.admin.password`/`PICAN_ADMIN_PASSWORD` y la **imprime en texto plano en consola**. | Si la consola se redirige a logs o es compartida, queda expuesta una credencial administrativa. | Corregir: no imprimir secretos; fallar cerrado en despliegue PostgreSQL con instrucciones de configuración. Conservar el modo MEMORY de demostración. Probar. |
| D-04 | **ALTO** | El cambio obligatorio de contraseña se comprueba solo en `LoginDialog:131-139`. `AuthService.login` (`:31-45`) acepta la sesión y `AutorizacionService.puedeAcceder/requerir` (`:49-77`) no consulta `requiereCambioContrasena()`. | Cualflow distinto del diálogo puede operar con contraseña temporal. Defensa en profundidad incompleta. | Corregir en la capa de autorización preservando bootstrap y cambio propio; probar regresión de autorización. |
| D-05 | **MEDIO** | `UsuarioSesionSupport.resolver` (`:14-18`) acepta el `idUsuario` pasado por el llamador antes que el de la sesión. | Atribución de auditoría manipulable desde servicios. La UI actual no lo explota. | Documentar como limitación; no alterar las firmas de servicios en esta etapa sin evidencia de uso indebido. |
| D-06 | **MEDIO** | `generarConfirmacion` (`:306-313`) incluye **todos** los conflictos `PENDIENTE` (`SyncRepositorio:131-134`), sin filtrar por paquete/UEB. | Una confirmación dirigida a una UEB puede arrastrar conflicto de otro paquete. | Corregir si la prueba es acotada; si no, documentar como observación. |
| D-07 | **MEDIO** | En conflictos, `registrarConflicto` (`SyncService:282-290`) graba un ítem con el motivo y el bucle `:218-221` lo graba de nuevo con otro detalle; `SyncRepositorio:89-102` aplica `ON CONFLICT DO UPDATE`. | Se sobrescribe el motivo de conflicto almacenado, degradando la evidencia. | Corregir eliminando la escritura duplicada; probar. |
| D-08 | **MEDIO** | `SyncRepositorio.listarPendientes` (`:207-215`) usa `sincronizado_en IS NULL OR eliminado_logico = TRUE OR activa = FALSE`, pero no `version > version_confirmada` pese a `DISEÑO...:121-127`. `registrarItem` escribe `version = NULL` (`:89-102`), por lo que `version_confirmada` queda en 0. | Reexportación de bajas ya confirmadas y no reexportación de filas editadas tras confirmación. | Corregir o, si el cambio altera el protocolo probado, documentar como desviación conocida con decisión técnica pendiente. |
| D-09 | **MEDIO** | Compensaciones de MEMORY que ignoran fallos: `RecepcionService:98-112`, `IncidenciaService:230-240`. | Posible estado parcial solo en modo demostración; PostgreSQL usa transacción real (`JdbcTransactionManager:19-53`). | Documentar. No cambiar reglas de negocio. |
| D-10 | **MEDIO** | `SyncService` escribe el ZIP antes de la transacción de metadatos (`:103-117`, `:335-343`). | Si falla el registro en `sync_paquete`, queda un archivo sin rastro en la base. | Documentar como limitación; no se simula rollback de sistema de archivos. |
| D-11 | **PENDIENTE EXTERNO** | Numeración de vales en memoria y avanzada antes de validar/guardar (`ValeEntregaService:26-32`, `:125-158`). | Se pierde al reiniciar y puede consumir un número sin vale. | Requiere decidir formato y mecanismo de secuencia persistente. No inventar. |
| D-12 | **BAJO** | `PermisosInterfaz.puedeAcceder/modulosPermitidos` son permisivos con rol `null` (`:62-73`), mientras `NavigationController:40-42` falla cerrado; existe `SideMenuPanel(null-role)` (`:22-24`) sin uso en producción. | Inconsistencia de defensa en profundidad, no explotable en el arranque real (`MainFrame:50` pasa el rol). | Alinear a fallo cerrado con prueba acotada. |
| D-13 | **BAJO** | Textos obsoletos: `AdminModulePanel:24-26` dice «Datos en memoria» y `ReceptionModulePanel:166` dice que CU-12 está pendiente, ambos implementados. | Información incorrecta al usuario. | Corregir textos. |
| D-14 | **BAJO** | `DISEÑO_SINCRONIZACION_PICAN.md:47,68,90` muestra números JSON (`cantidadRegistros: 12`, `version: 2`, `versionRecibida: 2`) que `SyncJson.Parser.valor()` (`:112-126`) rechaza; la implementación los escribe como cadenas. | Un paquete construido según el ejemplo documentado fallaría. | Corregir la documentación a cadenas, sin tocar el protocolo. |
| D-15 | **BAJO** | `config/pican.properties.example:22-23` presenta `pican.migrations.dir` como opción de archivo, pero `MigrationRunner:40-57` solo lee la propiedad de sistema. | Configación silenciosamente ignorada. | Corregir el ejemplo indicando `-Dpican.migrations.dir`. |
| D-16 | **BAJO** | `scripts/ejecutar.ps1:19-21` comprueba una variable de entorno `pican.db.password` que `DatabaseConfig` no lee (`:101-102,139-151`). | Suprime una advertencia útil y el arranque falla después. | Corregir el script. |
| D-17 | **BAJO** | `BackupArchivo` limita a 1 GB en lectura (`:58-63,91-102`) pero no en `escribir` (`:37-52`). | Un dump sobredimensionado se escribe antes de ser rechazado. | Añadir comprobación simétrica con prueba acotada. |
| D-18 | **BAJO** | `ToolLocator:37-38,48-49,70-72` acepta candidato por `isRegularFile`, sin verificar ejecutabilidad; solo descubre PostgreSQL 12-17. | Error diferido a `ProcessBuilder.start()`. | Documentar en requisitos de despliegue (ya están documentadas las rutas en el manual §2). |
| D-19 | **BAJO** | Contradicciones documentales: `DIAGNOSTICO_CU19_FASE_0.md:23` dice 204 fuentes en 4.16 (fueron 189); `PRUEBAS_ETAPA_4.16.md:156` habla de ocho logs y enumera siete; identificador «SIN-13» reutilizado en 4.15 y 4.18; `PRUEBAS_ETAPA_4.18.md:5` dice «aprobado» frente a «aprobado con limitación» en el informe; `CU-17_NOTAS.md:14` pide un índice de unicidad que V1 **ya tiene** con `lower(btrim(codigo))` (`V1:61`). | Ruido documental y posible confusión al desplegar. | Corregir los textos. |
| D-20 | **BAJO** | Literales de contraseñas de prueba en `PRUEBAS_ETAPA_4.17.md:53,70,80,83` y `PRUEBAS_ETAPA_4.15.md` (comandos de preparación), y literal histórico en `test/.../AppContextIntegrationTest.java:33`. No aparecen en logs (`PRUEBAS_ETAPA_4.17.md:33`). | Incumple la convención de usar solo placeholders en documentos. | Sustituir en documentos por `<CLAVE_PG_PRUEBA>` / `<CLAVE_ADMIN_PRUEBA>`; conservar el arnés si su cambio obliga a repetir pruebas ya aprobadas, y documentarlo. |
| D-21 | **PENDIENTE EXTERNO** | SIN-13 completo: PICAN-SYNC 1.0 no define la bajada central con `ultimo_vale_conocido` ni última secuencia (`MANUAL_COPIAS_SEGURIDAD_PICAN.md:77-79,110`; `DISEÑO_CU19.md:84-92`). | Imposible desbloquear una UEB restaurada sin inventar el contrato. | No implementar nada; documentar la decisión técnica requerida. |
| D-22 | **PENDIENTE EXTERNO** | No existen plantillas XLSX físicas oficiales; CU-18 se probó contra la estructura normalizada implementada (`INFORME_FINAL_ETAPA_4.17.md:95`). | Compatibilidad real no verificada. | Esperar archivos de PICAN; no inventar plantillas. |
| D-23 | **PENDIENTE EXTERNO** | Sin relación usuario↔UEB, sin reglas de precedencia/resolución de conflictos, sin autoridad de escritura por tabla, sin distribución central→UEB, sin frecuencia de intercambio, CU-04 fuera de alcance, sin listado oficial de 28 unidades. | Impide funciones empresariales que no pueden simularse sin decisión. | Mantener en `PENDIENTES_PICAN.md`. |
| D-24 | **NO PROBADO** | Ninguna prueba manual de Swing fue ejecutada en 4.14-4.18 (`PRUEBAS_MANUALES_GUI_4.15.md:1-3`). | No se puede afirmar apariencia, interacción ni mensajes visuales. | Generar `PRUEBAS_MANUALES_GUI_ETAPA_4.19.md` con pasos, datos, rol y resultado esperado; no declarar ejecución. |
| D-25 | **BAJO** | `DashboardModulePanel:13-44` muestra tarjetas y tabla explícitamente etiquetadas como ficticias. | No es un indicador inventado como real, perono muestra datos operativos. | Documentar que requiere definición de indicadores antes de sustituirlo (PENDIENTE EXTERNO para datos reales). |
| D-26 | **BAJO** | Copias antiguas sin seguimiento fuera del directorio permitido (`aplicacionPican.rar`, `Modelo Caso de uso de Negocio/aplicacionPican/`). | Riesgo de confundir versiones al desplegar. | Mencionar en el informe; no eliminar ni modificar. |

---

## 3. Prioridad de corrección en 4.19

1. **D-01** (integridad de sincronización), **D-02** (última cuenta administrativa), **D-03** (secreto en consola), **D-04** (cambio obligatorio en servicios).
2. **D-07**, **D-06**, **D-08**, **D-12**, **D-17** si cada una cuenta con prueba acotada y no altera decisiones aprobadas.
3. **D-13 a D-20**, **D-25**, **D-26**: correcciones documentales/script/UI de bajo riesgo.
4. **D-05**, **D-09**, **D-10**, **D-11**, **D-18**: documentar como limitaciones; no simular soluciones.
5. **D-21 a D-24**: bloqueos externos o no probados; declararlos explícitamente en el informe final.

Toda corrección deberá acompañarse de una prueba ejecutada. No se repetirán las 145 comprobaciones JDBC ni las baterías 4.15-4.18 sin un cambio que lo justifique.

---

## 4. Resolución efectiva en Etapa 4.19

| ID | Estado final | Resultado |
|---|---|---|
| D-01 | **Corregido** | `importarConfirmacion` respeta `RECHAZADO`; prueba D-01.a-d en base temporal. |
| D-02 | **Corregido** | Guarda de última cuenta ADMINISTRADOR en `asignarRol` y `actualizarDatos`; prueba D-02.a-d. |
| D-03 | **Corregido** | Arranque PostgreSQL falla cerrado sin credencial; nunca se imprime; prueba D-03.a-e. |
| D-04 | **Observación / no corregido** | Reforzar la capa de servicios invalidaría el arnés 4.16 aprobado; se documenta. |
| D-05 | **Observación** | No hay uso indebido demostrado; no se cambian firmas. |
| D-06 | **Corregido** | `generarConfirmacion` filtra conflictos por `id_paquete`; prueba D-06.a-b. |
| D-07 | **Corregido** | Se eliminó la doble escritura del detalle de conflicto; prueba D-07.a-b. |
| D-08 | **Desviación documentada** | Cambiar la regla de pendientes afectaría la evidencia 4.15; requiere decisión técnica. |
| D-09 a D-11 | **Observaciones** | No se simulan compensaciones, rollback de archivos ni secuencias sin decisión. |
| D-12 | **Observación / no corregido** | `PermisosInterfazTest:42-43` aprueba explícitamente el rol `null` permisivo; alinearlo rompería esa evidencia. |
| D-13 a D-16 | **Corregido** | Textos de UI, ejemplos JSON, `pican.migrations.dir` y `ejecutar.ps1`. |
| D-17 | **Observación / no corregido** | No se añadió la comprobación simétrica de tamaño en `BackupArchivo.escribir`; se registra como limitación. |
| D-18 | **Observación** | Requisitos de ejecutables ya documentados en el manual §2. |
| D-19 | **Corregido** | Cifras, número de logs, identificación de SIN-13, estado 4.18 e índice de V1. |
| D-20 | **Corregido en documentos** | Placeholders en 4.15, 4.16 y 4.17; se conserva el literal histórico del arnés 4.16 para no repetir su evidencia. |
| D-21 a D-23 | **BLOQUEADO / PENDIENTE EXTERNO** | Requieren contrato, plantillas o decisiones de PICAN. |
| D-24 | **NO PROBADO** | No hubo interacción Swing; se entrega plan manual. |
| D-25 | **Corregido documentalmente** | El panel especifica que requiere definición de indicadores. |
| D-26 | **Mencionado** | Copias sin seguimiento fuera del alcance; no se tocan. |
