# MATRIZ DE PERMISOS — PICAN (Etapas 4.16—4.18)

Estado: **autorización integral por roles implementada y verificada para CU-01 a CU-19**. La autoridad central decide en cada servicio; la interfaz deriva su matriz de la misma fuente. CU-18 quedó implementado en la Etapa 4.17 y CU-19 en la Etapa 4.18; ambos siguen reservados al ADMINISTRADOR. CU-04 continúa fuera de alcance por decisión documental.

Fuente de los hechos: `src/cu/pican/service/CasoUso.java`, `AutorizacionService.java`, `ServicioAutorizado.java`, `ImportacionService.java`, `src/cu/pican/app/AppContext.java`, `src/cu/pican/view/PermisosInterfaz.java`, `controller/NavigationController.java`, `INFORME_FINAL_ETAPA_4.16.md`, `INFORME_FINAL_ETAPA_4.17.md`, `INFORME_FINAL_ETAPA_4.18.md`, DCUS reflectados en `Rol.java`. No se introducen reglas empresariales nuevas.

---

## 1. Autoridad central y principios

| Elemento | Función |
|---|---|
| `CasoUso` | Catálogo único CU → roles responsables. `CU_18` y `CU_19` solo declaran al ADMINISTRADOR; `CU_04` permanece deshabilitado. |
| `AutorizacionService` | Única decisión de acceso a partir de la sesión existente. Sin política instalada, **falla cerrada**. |
| `SesionAutorizacionProvider` | Puente con `AuthService`; la autoridad no conoce Swing. |
| `AutorizacionException` | Rechazo controlado, distinto de `ValidacionException` y de `SyncException`. |
| `ServicioAutorizado` | Cada servicio conserva la autoridad de su contexto; `AppContext` la inyecta. Varios contextos pueden coexistir sin compartir sesión. |
| `PermisosInterfaz` | Matriz de UI derivada de `CasoUso`, no una lista paralela. |

`ADMINISTRADOR` **no es superusuario**: no produce, no emite vales, no confirma recepciones, no gestiona incidencias y no elabora informes.

---

## 2. Actores y casos de uso asignados (DCUS)

| Actor | Casos de uso | Observación |
|---|---|---|
| **ADMINISTRADOR** | CU-16 usuarios · CU-17 catálogos · CU-18 importación Excel **implementada** · CU-19 copias de seguridad **implementada** | Sin permisos de producción, vales, recepciones, incidencias ni análisis. |
| **JEFE_DE_UEB** | CU-05 huevos · CU-06 carne · CU-07 consolidación · CU-08 paquetes · CU-10 vales · CU-11 recepciones · CU-12 incidencias · CU-13 anulaciones | "Dentro de su UEB" **no implementable hoy**: no existe relación usuario↔UEB. No se inventó UEB predeterminada ni cambio arbitrario de UEB. |
| **ESPECIALISTA_DE_ESTADISTICA** | CU-09 recepción/validación/importación en central · CU-14 análisis · CU-15 informes y decisión comunicada por el Directivo | El Directivo no inicia sesión; el especialista registra su decisión. |
| **ESPECIALISTA_DE_PRODUCCION** | CU-01 referencias · CU-02 metas · CU-03 infraestructura · CU-04 camiones/choferes *(fuera de alcance)* | Consulta de referencias oficiales; sin escrituras de catálogo ni importaciones. |
| **DIRECTIVO** | Ninguno en el sistema | `Rol.predefinido("DIRECTIVO")` devuelve `null`; no se crea cuenta. |

---

## 3. Matriz real: módulo/CU, rol, UI y servicio

Leyenda: **Sí** = permitido y comprobado; **No** = rechazado en servicio y oculto en UI; **Reservado** = declarado en la matriz pero sin implementación.

| Módulo / CU | Rol responsable | UI filtrada | Servicio protegido | Estado |
|---|---|---|---|---|
| Inicio / dashboard | Cualquier cuenta activa | Sí | No requiere CU | Implementado |
| CU-01 referencias históricas | PRODUCCIÓN (consulta), ESTADÍSTICA (apoyo) | Sí | `AnalisisService` | Implementado |
| CU-02 metas estratégicas | PRODUCCIÓN, ESTADÍSTICA | Sí | `AnalisisService` | Implementado |
| CU-03 infraestructura | PRODUCCIÓN, ESTADÍSTICA | Sí | `AnalisisService` | Implementado |
| CU-04 camiones/choferes | — | No | No | **Fuera de alcance** |
| CU-05 partes de huevos | JEFE_DE_UEB | Sí | `ParteNaveService`, `ParteUnidadAsociadaService` | Implementado |
| CU-06 producción de carne | JEFE_DE_UEB | Sí | `ProduccionCarneService` | Implementado |
| CU-07 consolidación | JEFE_DE_UEB | Sí | `ConsolidadoUEBService` | Implementado |
| CU-08 exportación USB | JEFE_DE_UEB | Sí | `SyncService` | Implementado (4.15, centralizado en 4.16) |
| CU-09 importación/validación central | ESPECIALISTA_DE_ESTADISTICA | Sí | `SyncService` | Implementado (4.15, centralizado en 4.16) |
| CU-10 vales de entrega | JEFE_DE_UEB | Sí | `ValeEntregaService` | Implementado |
| CU-11 recepciones | JEFE_DE_UEB | Sí | `RecepcionService`, `IncidenciaService` | Implementado |
| CU-12 incidencias | JEFE_DE_UEB | Sí | `IncidenciaService`, `ValeEntregaService` | Implementado |
| CU-13 anulación de vales | JEFE_DE_UEB | Sí | `ValeEntregaService` | Implementado |
| CU-14 análisis operativo | ESPECIALISTA_DE_ESTADISTICA | Sí | `AnalisisService` | Implementado |
| CU-15 informes y decisión | ESPECIALISTA_DE_ESTADISTICA | Sí | `InformeService` | Implementado |
| CU-16 usuarios y roles | ADMINISTRADOR | Sí | `UsuarioService` | Implementado (bootstrap y cambio propio permitidos sin CU) |
| CU-17 catálogos | ADMINISTRADOR | Sí | `UEBService`, `NaveService`, `UnidadAsociadaService`, `ProductoService`, `DestinoService`, `ProductoDestinoService` | Implementado |
| CU-18 importación Excel | ADMINISTRADOR | Sí (pestaña `Importar datos`) | `ImportacionService` + `ImportacionReferenciasRepository` | **Implementado — Etapa 4.17** |
| CU-19 copias de seguridad | ADMINISTRADOR | Sí (pestaña `Copias de seguridad`) | `BackupService` + `PostgresBackupOperations` | **Implementado — Etapa 4.18** |
| Referencias (solo lectura) | PRODUCCIÓN, ESTADÍSTICA | Sí | `AnalisisService` / catálogos | Implementado (`ReferenciasModulePanel`) |

Lectura de catálogos: cualquier cuenta activa con rol previsto puede consultar UEB, naves, unidades, productos y destinos como soporte técnico de su propio CU. **No es un permiso empresarial nuevo**; las escrituras siguen exigiendo CU-17.

---

## 4. Defensa en profundidad

1. **Servicios**: `AutorizacionService.requerir(CasoUso...)` antes de cada operación; rechazo antes de validar datos o escribir.
2. **Navegación**: `NavigationController.aplicarPermisos` + `puedeMostrar` bloquean `showModule` directo. Sin matriz instalada falla cerrado.
3. **Interfaz**: `SideMenuPanel` solo crea botones permitidos; `MainFrame` construye paneles con `Supplier<JPanel>` para no consultar servicios ajenos al rol en el constructor.
4. **Sesión**: `AuthService` es la única fuente de identidad; `Rol.predefinido("DIRECTIVO")` no existe.
5. **Sincronización**: `SyncService` delega en la autoridad central pero conserva `SyncException` hacia la UI y arneses 4.15.
6. **Datos de demostración**: `DemoDataInitializer` usa una política temporal de demo **sin sesión** y restaura la autoridad real; no deja permisos permisivos activos.

---

## 5. Límites y precisiones pendientes

| Tema | Decisión |
|---|---|
| Vínculo usuario↔UEB | No existe. No se puede aplicar "Jefe de UEB solo en su UEB". No se inventó tabla, campo, UEB predeterminada ni cambio de UEB. |
| Permisos internos por CU | Los DCUS no precisan lectura/creación/modificación/eliminación por tabla. Solo se implementó lo documentado; el resto no se inventa. |
| Autoridad de escritura por tabla (UEB vs central) | Sigue pendiente, herencia de 4.15. La sincronización aplica reglas técnicas de integridad, no decisiones empresariales nuevas. |
| Resolución de conflictos de sincronización | El motor detecta y conserva evidencia; la decisión humana sigue pendiente. |
| Frecuencia de intercambio USB y flujos central→UEB | Pendiente. |
| CU-18 | Implementado en 4.17: solo ADMINISTRADOR, comprobación en servicio antes de preparar y antes de confirmar. No se sobrescriben claves existentes. |
| CU-19 | Implementado en 4.18: solo ADMINISTRADOR, copias reales con `pg_dump`, restauración verificada y bloqueo local SIN-13. La bajada completa del central sigue pendiente en PICAN-SYNC 1.0. |
| CU-04 | Fuera de alcance. |

---

## 6. Evidencia y cierre

- `AutorizacionRolesTest`: **21/21** en MEMORY y **21/21** en POSTGRES (cuatro roles, sin sesión, navegación directa, llamadas directas a servicios y ausencia de escrituras parciales).
- `PermisosInterfazTest`: **6/6**.
- `ImportacionExcelTest`: **15/15** en MEMORY y **15/15** en POSTGRES (válidas, rechazadas, repetidas, errores estructurales, seguridad de archivo, recomprobación de sesión y roles).
- Regresiones afectadas: `AppContextIntegrationTest` **12/12**, `ValidacionIntegralPostgresTest` **20/20**, `SincronizacionPostgresTest` **14/14**, `ServiciosTransaccionalesTest` **5/5** (4.16); `PermisosInterfazTest` y `AutorizacionRolesTest` repetidos en 4.17.
- Compilación oficial: **OK · 204 fuentes compiladas en `out`**; 10 fuentes de prueba compiladas.
- Pruebas manuales GUI: preparadas, **no ejecutadas**; no se afirma lo contrario.

Con esto se cierran la Etapa 4.16 y la Etapa 4.17. CU-18 queda **implementado y verificado** para los cuatro tipos oficiales derivados de las tablas históricas aprobadas.
