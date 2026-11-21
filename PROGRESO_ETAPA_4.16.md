# PROGRESO — ETAPA 4.16 (PICAN · Autorización integral por roles)

Fuente de verdad: `C:\Users\Ariel David\Desktop\bioproductos\aplicacionPican\`
Fecha de inicio y cierre: 2026-11-20.

> Registro vivo cerrado al terminar la etapa. No sustituye a la implementación ni a las evidencias de `PRUEBAS_ETAPA_4.16.md`.

---

## Estado general

- **Fase actual:** Fase 8 — Informe final y cierre (completada).
- **Fases completadas:** 0, 1, 2, 3, 4, 5, 6, 7 y 8.
- **Objetivo cumplido:** autorización centralizada por rol en todos los servicios operativos vigentes, con UI derivada de la misma matriz. No se rehicieron funcionalidades aprobadas en 4.15.
- **Git:** raíz `C:/Users/Ariel David/Desktop/bioproductos`, HEAD `33bdf0d`; `aplicacionPican/` continúa sin seguimiento (`?? aplicacionPican/`). Sin commits, push, reset ni cambios destructivos.
- **Copia de seguridad previa:** `C:\Users\Ariel David\AppData\Local\Temp\opencode\pican-etapa-4.16-backup-20261120-152720` (200 archivos).
- **Compilación oficial:** `.\scripts\compilar.ps1` → **OK: 189 fuentes compiladas en `out`**.
- **Entorno:** PostgreSQL 17.6 temporal en `127.0.0.1:55432`, usuario `pican_test`, autenticación `trust`. No se tocó el servicio en `5432`.
- **Bases exclusivas de pruebas:** `pican_autorizacion_416_test`, `pican_appcontext_416_test`, `pican_validacion_416_test`, `pican_servicios_416_test`, `pican_sync_ueb_a_test`, `pican_sync_ueb_b_test`, `pican_sync_central_test`. Todas contienen `test`.
- **Credenciales:** contraseñas de prueba pasadas por `-D`/`System.setProperty`; no hay credenciales reales en código, logs ni documentos.
- **Librerías:** solo `lib/postgresql-42.7.13.jar`. Apache POI queda para evaluación en 4.17.

---

## Fase 0 — Diagnóstico y matriz real (COMPLETADA)

Antes de modificar código se entregó el diagnóstico con la matriz `Módulo/CU | Rol responsable | UI filtrada | Servicio protegido | Estado`.

Hallazgos:
1. Menú y navegación ya filtraban por rol, pero los servicios de negocio no comprobaban roles (salvo invariantes internos de `UsuarioService` y el módulo de sincronización de 4.15).
2. `SyncService` ya protegía CU-08/CU-09, con `SyncException` hacia la UI.
3. No existe relación usuario↔UEB. No se podía implementar "Jefe solo en su UEB" sin inventar estructura, UEB predeterminada o cambio de UEB.
4. `DIRECTIVO` no tiene cuenta ni rol predefinido; el DCUS lo mantiene fuera del sistema.
5. CU-18 y CU-19 son responsabilidad del ADMINISTRADOR en la documentación, pero no están implementados; CU-04 quedó fuera de alcance.

**No existía bloqueo empresarial para cerrar 4.16.**

---

## Fase 1 — Autoridad central (COMPLETADA)

- `CasoUso.java`: catálogo CU → roles. `CU_18` declara solo ADMINISTRADOR; `CU_04`/`CU_19` deshabilitados.
- `AutorizacionService.java`: única decisión de acceso, **fail closed** si no hay política instalada.
- `AutorizacionException.java`: rechazo controlado.
- `SesionAutorizacionProvider.java`: puente sin Swing hacia `AuthService`.
- `ServicioAutorizado.java`: cada servicio guarda la autoridad de su contexto; `AppContext` la inyecta. Esto permite varios contextos en un mismo proceso sin mezclar sesiones.
- `AutorizacionDemo.java`: sesión de demostración explícita para demos/arneses históricos. No hay permisivo por defecto.

---

## Fase 2 — Servicios protegidos (COMPLETADA)

Se añadió `requerir(CasoUso...)` antes de cada operación en:

- Catálogos (CU-17): UEB, naves, unidades asociadas, productos, destinos y relaciones producto-destino. Lectura disponible para cualquier cuenta activa como soporte técnico; no es un permiso empresarial nuevo.
- Producción (CU-05/06/07): partes de huevos, partes de unidades, carne y consolidados.
- Vales y flujo (CU-10/11/12/13): emisión, transiciones, recepciones, incidencias y anulaciones.
- Análisis e informes (CU-01/02/03/14/15).
- Usuarios (CU-16): bootstrap controlado y cambio de contraseña propia.
- `SyncService`: delega en la autoridad central conservando `SyncException` y la validación temprana del paquete.

No se crearon tablas, roles nuevos ni vínculos UEB.

---

## Fase 3 — Interfaz y navegación (COMPLETADA)

- `PermisosInterfaz` se reescribió desde `CasoUso`; la UI ya no mantiene una matriz paralela.
- `SideMenuPanel` y `MainFrame` usan esa matriz; `MainFrame` registra paneles con `Supplier<JPanel>` para no consultar servicios ajenos al rol en constructores.
- Nuevo `ReferenciasModulePanel` de solo lectura para PRODUCCIÓN.
- `NavigationController` falla cerrado si existe matriz instalada pero no hay rol.

---

## Fase 4 — Arnés específico 4.16 (COMPLETADA)

`test/cu/pican/verify/AutorizacionRolesTest.java` con AUT-01 a AUT-21 cubre:

- Matriz central de los cuatro roles y separación de responsabilidades.
- DIRECTIVO sin cuenta ni rol posible.
- CU-18 reservado al ADMINISTRADOR, todavía no implementado.
- Sin sesión: lectura de catálogos y de producción rechazadas en servicio.
- Navegación directa a módulos ajenos al rol.
- ADMIN sin producción, vales ni análisis; JEFE sin usuarios ni informes; ESTADÍSTICA sin recepciones; PRODUCCIÓN sin escrituras de catálogo.
- El rechazo por rol no deja escrituras parciales.

Resultado: **21/21 en MEMORY y 21/21 en POSTGRES**.

---

## Fase 5 — Regresiones 4.15 afectadas (COMPLETADA)

Solo se repitieron arneses tocados por `AppContext`, la autoridad central o la construcción de servicios:

| Arnés | Resultado | Motivo de inclusión |
|---|---|---|
| `PermisosInterfazTest` | 6/6 | Matriz UI derivada de `CasoUso`. |
| `AppContextIntegrationTest` | 12/12 | Instalación de la política y siembra de demo. |
| `ValidacionIntegralPostgresTest` | 20/20 | Servicios con sesiones por rol reales. |
| `SincronizacionPostgresTest` | 14/14 | Centralización de autoridad en `SyncService` y tres contextos aislados. |
| `ServiciosTransaccionalesTest` | 5/5 | Cambio de base `ServicioAutorizado` en servicios directos. |

Los arneses no afectados no se repitieron. Las siembras históricas de 4.15 se adaptaron con sesiones reales o con una política de demostración explícita y restaurada; no se cambiaron reglas de negocio.

---

## Fase 6 — Documentación (COMPLETADA)

- `MATRIZ_PERMISOS_PICAN.md` actualizado con la matriz real y los pendientes.
- `PRUEBAS_ETAPA_4.16.md` con comandos y evidencias.
- `INFORME_FINAL_ETAPA_4.16.md` con cierre.

---

## Fase 7 — Pruebas manuales GUI

La lista de pruebas manuales GUI de 4.15 sigue disponible. **No se ejecutaron en esta etapa**; no bloquean el cierre automatizado. No se afirma lo contrario.

---

## Fase 8 — Cierre (COMPLETADA)

La Etapa 4.16 queda cerrada con autorización integral verificada para CU-01…CU-17. CU-18 queda estrictamente como siguiente etapa: solo ADMINISTRADOR, con comprobación también en servicio, sin plantillas ni entidades inventadas.

---

## Decisiones que se mantienen

- La sesión real de `AuthService` es la única fuente de identidad.
- Fail closed: sin política instalada, las operaciones protegidas se rechazan.
- Demos y arneses históricos instalan o restauran explícitamente una política de demostración; no hay permisivo por defecto.
- `SyncException` se conserva hacia UI/arneses.
- No se inventó relación usuario↔UEB, UEB predeterminada ni cambio arbitrario de UEB.
- No se declaró CU-18 implementado.
