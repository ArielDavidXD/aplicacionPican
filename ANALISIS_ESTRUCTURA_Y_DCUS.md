# ANÁLISIS DEL PROYECTO PICAN: ESTRUCTURA DE CARPETAS Y CORRESPONDENCIA CON EL DCUS

Generado el 2026-11-24. Compara la implementación Java (`src/`) contra el DCUS
(Documento de Casos de Uso del Sistema) con los 19 casos de uso.

---

## PARTE A · Qué hay en cada carpeta del código

| Carpeta | Archivos | Contenido |
|---|---|---|
| `src/cu/pican/app` | 21 | Arranque y demostraciones: `PicanApp` (punto de entrada Swing), `AppContext` (contenedor de dependencias: arma 20 repositorios y servicios en modo memoria o PostgreSQL, ejecuta migraciones), `UsuarioInicialInitializer` (cuenta admin inicial), `DemoDataInitializer` (datos de demostración solo en memoria), `ReferenciasHistoricasJdbcInitializer` (tablas históricas oficiales en PostgreSQL), demos de consola por etapa (`*StageDemo`, `RepositoryDemo`, `ServiceDemo`, `AutorizacionDemo`). |
| `src/cu/pican/backup` | 14 | Copias de seguridad (CU-19): `PgBackupOperations`/`PostgresBackupOperations` (pg_dump/pg_restore), `ToolLocator` (localiza herramientas de PostgreSQL), `VerificadorDestino`, `ValidadorCopia`, `ManifiestoCopia` (metadatos y SHA), `InformacionCopia`, `ResultadoCopia`/`ResultadoRestauracion`, `EstadoEsquema`, `VersionAplicacion`. |
| `src/cu/pican/controller` | 1 | `NavigationController`: navegación por módulos (CardLayout) con permisos. |
| `src/cu/pican/importacion` | 10 | Importación Excel (CU-18): `LectorXlsx` (parser propio de .xlsx vía ZIP+XML, sin librerías externas), `PlantillaOficial` (cabeceras oficiales), `ValidadorImportacion`, `ArchivoExcel`/`FilaExcel`/`ResultadoFila`/`ResultadoImportacion`/`EstadoFila`, `TipoImportacion`. |
| `src/cu/pican/model` | 30 | Entidades de dominio: UEB, Nave, ParteNave, ParteUnidadAsociada, ProduccionCarne, ConsolidadoUEB, ValeEntrega, Recepcion, Incidencia, IndicadorHistorico, MetaEstrategica, Informe, FuenteInforme, DecisionDirectivo, EntregaInforme, DestinatarioInstitucional, Rol, Usuario, etc. |
| `src/cu/pican/persistence` | 9 | Capa de persistencia: `DatabaseConfig` (memory/postgresql, prioridad -D > env > archivo > defecto), `JdbcConnectionManager`, `JdbcTransactionManager`, `MigrationRunner` (aplica `sql/migrations` V1–V3), `JdbcRepositorySupport`/`JdbcSupport`, `NoOpTransactionManager`, `TransactionManager`, `PersistenciaException`. |
| `src/cu/pican/repository` | 24 | Interfaces de repositorio por entidad (contratos independientes de la tecnología). |
| `src/cu/pican/repository/jdbc` | 23 | Implementaciones PostgreSQL de los repositorios (SQL + UUID + versionado). |
| `src/cu/pican/repository/memory` | 25 | Implementaciones en memoria (solo demostración; se pierden al cerrar). |
| `src/cu/pican/service` | 30 | Servicios de negocio por caso de uso (autorización antes de operar): `ParteNaveService` (CU-05), `ProduccionCarneService` (CU-06), `ConsolidadoUEBService` (CU-07), `ValeEntregaService` (CU-10/13), `RecepcionService` (CU-11), `IncidenciaService` (CU-12), `AnalisisService` (CU-01..03/14), `InformeService` (CU-15), `UsuarioService`/`AuthService` (CU-16), catálogos (CU-17), `ImportacionService` (CU-18), `BackupService` (CU-19), `AutorizacionService`, `CasoUso` (catálogo de CUs con roles), `PasswordHasher` (PBKDF2-SHA256). |
| `src/cu/pican/sync` | 7 | Sincronización USB (CU-08/09): `SyncService` (generar/importar paquetes y confirmaciones), `SyncRepositorio` (tablas `sync_*`), `SyncArchivo` (ZIP en memoria con límites y SHA-256), `SyncJson`, `SyncEsquema` (tablas exportables y orden), `SyncModelo` (registros, conflictos, resultados), `SyncException`. |
| `src/cu/pican/view` | 6 | Ventanas base: `MainFrame`, `LoginDialog`, `CambioContrasenaDialog`, `HeaderPanel`, `SideMenuPanel`, `PermisosInterfaz` (matriz módulo↔rol). |
| `src/cu/pican/view/components` | 6 | Componentes reutilizables: `AppButton`, `DataTablePanel`, `MessageHelper`, `ModuleHeader`, `UITheme`, `BarChartPanel`. |
| `src/cu/pican/view/modules` | 22 | Un panel por módulo: `Dashboard`, `Referencias`, `EggProduction`, `MeatProduction`, `Consolidation`, `Deliveries`, `Reception`, `Incidents`, `Reports` (+`Analysis`, `ReportManagement`), `Sync`, `Admin` (+subpaneles `UEB`, `Nave`, `UnidadAsociada`, `Producto`, `Destino`, `ProductoDestino`, `Usuario`, `Importacion`, `CopiasSeguridad`). |
| `sql/migrations` | 3 | Migraciones: `V1__esquema_inicial.sql` (dominio), `V2__sincronizacion.sql` (sync_paquete/sync_item/sync_conflicto), `V3__backup_auditoria.sql` (auditoría y control de restauración). |
| `config` | 2 | `pican.properties.example` y `pican.properties` local (excluido de git; contraseña por variable de entorno). |
| `lib` | 1 | Driver JDBC `postgresql-42.7.13.jar` (única dependencia externa). |
| `scripts` | 2 | `compilar.ps1` y `ejecutar.ps1` (modos memoria/postgresql). |
| `test/cu/pican/verify` | 12 | Pruebas de integración contra PostgreSQL (sincronización, transacciones, copias, importación Excel, autorización). |

---

## PARTE B · ¿Corresponde la app con el DCUS?

### B.1 Roles: COINCIDEN ✅

| Rol del DCUS | Rol en la app | Módulos que ve |
|---|---|---|
| Jefe de UEB | `JEFE_DE_UEB` (CU-05..08, CU-10..13) | Producción huevos/carne, Consolidación, Entregas, Recepción, Incidencias, Sincronización |
| Especialista de Producción | `ESPECIALISTA_DE_PRODUCCION` (CU-01..03) | Referencias |
| Especialista de Estadística | `ESPECIALISTA_DE_ESTADISTICA` (CU-09, 14, 15) | Análisis e informes, Sincronización (lado central) |
| Administrador | `ADMINISTRADOR` (CU-16..19) | Administración |

Los naveros no son usuarios del sistema (sus datos los registra el Jefe), tal como pide el DCUS.

### B.2 Veredicto por caso de uso

Leyenda: ✅ implementado · ⚠️ parcial · ❌ no implementado

| CU | Veredicto | Qué cumple | Qué falta o difiere |
|---|---|---|---|
| CU-01 Referencias históricas | ✅ | Solo lectura; tabla por período/renglón/unidad/cantidad/fuente/versión/estado (`AnalisisService`, `ReferenciasModulePanel`). | — |
| CU-02 Metas | ⚠️ | Consulta de metas y comparación con producción (`ComparacionMeta`, cálculo de % de incremento). | El DCUS lo llama «Registrar meta»; en la app los valores entran por CU-18 (Excel), no hay alta manual. |
| CU-03 Infraestructura | ⚠️ | Consulta de la serie 2015–2019 (Tabla clave 1); no altera valores originales. | Igual que CU-02: la actualización es vía importación Excel, no edición directa. |
| CU-04 Camiones y choferes | ✅ (fuera de alcance) | Documentado como fuera de la versión 1 en `CasoUso` (sin roles). | — |
| CU-05 Parte diario | ⚠️ | Enteros no negativos; rotos ≤ producidos; enfermas ≤ vivas (y muertas ≤ vivas); nave activa; usuario existe; observaciones; partes de unidades asociadas (papel) con `ParteUnidadAsociada`. | **No** valida fecha futura, ni pienso con máx. 2 decimales, ni observaciones ≤ 250; no calcula huevos buenos (producidos−rotos); no avisa si producidos > vivas; **no impone** unicidad UEB+nave+fecha; no existe flujo Borrador/Registrado (el campo `estado` está «pendiente de definición»). |
| CU-06 Producción de carne | ✅ | Registro por UEB y fecha en toneladas, cantidad única, UEB activa (`ProduccionCarneService`). | Menor: la app permite cantidad 0 (el DCUS dice positiva) y no fuerza la UEB desde la sesión del usuario. |
| CU-07 Consolidar UEB | ⚠️ | CRUD del consolidado con período y UEB activa (`ConsolidadoUEBService`). | **No** calcula estado completo/parcial/retraso, ni conteos de partes llegados/faltantes/en borrador/con aclaración, ni resúmenes separados de huevos y carne con motivo (el propio código lo declara «pendiente»). |
| CU-08 Enviar parte al central | ⚠️ | Genera paquete ZIP acumulativo con los pendientes (`SyncService.generarExportacion`), estados GENERADO/RECIBIDO/CONFIRMADO, importa la confirmación del central, bloqueo tras restauración (SIN-13). | No usa nombre temporal + renombrado + relectura al escribir; no liga el estado del consolidado (Consolidado→Enviado); no usa convención `.avipkg` ni secuencia por UEB; no chequea reloj/UTC. |
| CU-09 Recibir paquetes en central | ⚠️ | Integridad SHA-256, formato/protocolo/esquema, rechazo completo de paquetes dañados, transacción única, un registro rechazado no frena a los demás, estados por ítem, generación de confirmación de vuelta. | **No** hay vista previa por registro antes de importar; **no** genera paquete de bajada (DN) completo; **no** mantiene la lista «UEB sin reportar». |
| CU-10 Emitir vale | ⚠️ | Estado EMITIDO, producto/destino/relación autorizada validados, cantidad > 0, número consecutivo por UEB que no se reutiliza (`ValeEntregaService.emitir`). | **No** hay código QR, ni vista previa/impresión de 2 ejemplares. Además el contador es **en memoria** (se reinicia al cerrar la app; el propio javadoc advierte que debe pasar a PostgreSQL). |
| CU-11 Confirmar recepción | ✅ | Cantidad recibida, responsable, fecha, calidad; CERRADO si conforme o RECIBIDO_CON_INCIDENCIA + incidencia automática (`RecepcionService`). Vale debe estar EMITIDO. | Menor: no valida que la fecha de recepción sea ≥ a la emisión ni que no sea futura. |
| CU-12 Incidencias | ✅ | Tipo, cantidad afectada, medida acordada, fecha compromiso; ABIERTA/CERRADA; el vale pasa a CERRADO al cerrar la última incidencia (`IncidenciaService` + `cerrarValeConIncidenciasResueltas`); reposición con vale nuevo. | — |
| CU-13 Anular/reimprimir vale | ⚠️ | Anulación lógica solo si EMITIDO, sin recepción, con confirmación explícita y motivo (`ValeEntregaService.anular`). | **No** existe reimprimir con marca DUPLICADO/ANULADO. |
| CU-14 Analizar resultados | ⚠️ | Consulta las 4 tablas de referencia, huevos/carne por período, UEB y nave, consolidaciones, comparación contra metas (`AnalisisService`). | **No** hay despachos/recepciones por destino, ni incidencias abiertas/cerradas en el análisis, ni «UEB sin reportar», ni exportación a PDF/Excel (el código lo declara «sin PDF/Excel»). |
| CU-15 Generar y entregar informe | ⚠️ | Creación/revisión de informes, fuentes asociadas, decisiones del directivo registradas por el especialista, entrega a destinatario institucional (GEGAN/MINAG) (`InformeService`). | Estados de la app: ELABORADO → DEVUELTO → APROBADO → ENTREGADO (el DCUS pide Borrador → En revisión → Aprobado → Entregado). **No** hay las 8 secciones estructuradas ni exportación PDF/Excel. |
| CU-16 Gestionar usuarios | ✅ | Alta con contraseña temporal de un solo uso, cambio obligatorio en primer acceso (`CambioContrasenaDialog`), activar/desactivar sin borrar, roles, UEB del jefe (`UsuarioService`). | La propagación de una cuenta desactivada a la UEB por USB (DN) no existe (no hay DN). |
| CU-17 Gestionar catálogos | ⚠️ | UEB, naves, unidades asociadas, productos, destinos y relaciones; desactivar en lugar de borrar cuando hay datos (`*AdminPanel`, `*Service` con `setVerificadorOperaciones`). | **No** genera el paquete de alta (AL) al dar de alta una UEB. |
| CU-18 Importar desde Excel | ✅ | Plantilla oficial con cabeceras validadas, vista previa (preparar) y confirmación, resumen de cargados/rechazados/repetidos con motivo por fila (`ImportacionService`, `PlantillaOficial`, `LectorXlsx`). | — |
| CU-19 Copias de seguridad | ✅ | Copia con pg_dump + manifiesto con SHA-256, restauración con doble confirmación, historial de auditoría y bloqueo post-restauración para vales y paquetes (SIN-13) (`BackupService`). | — |

### B.3 Diseño de sincronización del DCUS vs. lo implementado

**Implementado ✅**

- Canal único USB, sin Internet; ZIP con manifiesto JSON y payload.
- Integridad SHA-256; protocolo y esquema versionados.
- `id_paquete` UUID para detectar repeticiones; importar dos veces no duplica (OMITIDO).
- Paquetes acumulativos: siempre llevan lo pendiente de confirmar.
- Versionado por registro (`version`); detección de conflictos que quedan PENDIENTES (sin resolución automática).
- Registros nuevos insertados con su lote y origen; tablas exportables en orden de dependencias (`SyncEsquema`).
- Instalación LOCAL (UEB) y CENTRAL con bases PostgreSQL separadas (`pican.db.mode`).
- Regla «nada se borra»: anulación con motivo, desactivación y nuevas versiones en catálogos y operaciones.

**No implementado ❌ (diferencias con el documento)**

- Tipos de paquete UP/DN/AL/AC/ACK: solo existen EXPORTACION (subida) y CONFIRMACION (recibo) + CONFLICTOS.
- Extensión `.avipkg` y convención de nombres (la app sugiere `.zip`).
- Paquete de bajada DN (catálogos, usuarios/roles y referencias del central hacia la UEB).
- Paquete de alta AL (al crear una UEB) y paquete AC (actualización de software).
- Secuencia numérica por UEB en los paquetes y verificación de reloj (UTC) entre equipos.
- Vista previa de registros antes de importar y lista «UEB sin reportar».
- Escritura con nombre temporal + renombrado + relectura de verificación al generar el paquete.

### B.4 Resumen

La aplicación **sí corresponde en lo esencial** al DCUS: los 4 actores, la matriz de
permisos por caso de uso, los flujos operativos de producción/entregas/incidencias,
la administración, el Excel y las copias de seguridad están implementados. Los casos
más completos son CU-10/11/12 (vales), CU-16, CU-18 y CU-19.

Las brechas principales son: (1) validaciones finas de CU-05, (2) cálculo automático
de consolidados (CU-07), (3) QR/impresión y contador persistente de vales (CU-10/13),
(4) exportación PDF/Excel y las 8 secciones del informe (CU-14/15) y (5) el protocolo
de sincronización completo (DN, AL, AC, vista previa, convención de nombres), del que
hoy solo existe la subida y el recibo.
