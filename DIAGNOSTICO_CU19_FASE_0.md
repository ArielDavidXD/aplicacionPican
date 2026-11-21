# Diagnóstico Fase 0 — CU-19 / Etapa 4.18

Fecha: 2026-11-20  
Proyecto: `aplicacionPican`  
Estado: **Fase 0 completada antes de programar**. No se modificó el historial Git ni el HEAD.

## 1. Alcance verificado

Se inspeccionó únicamente `aplicacionPican`, sin tocar `parcial2`, el frontend alternativo ni copias paralelas. Se revisaron:

- `src/cu/pican/app/AppContext.java`, `DatabaseConfig.java`, `JdbcConnectionManager.java`, `MigrationRunner.java`.
- `sql/migrations/V1__esquema_inicial.sql` y `V2__sincronizacion.sql`.
- `src/cu/pican/service/CasoUso.java`, `AutorizacionService.java`, `ServicioAutorizado.java`, `PermisosInterfaz.java`.
- `src/cu/pican/view/modules/AdminModulePanel.java` y los paneles administrativos existentes.
- `src/cu/pican/sync/` (protocolo PICAN-SYNC 1.0) y `ValeEntregaService.java`.
- Herramientas PostgreSQL disponibles: `pg_dump`, `pg_restore` y `psql` 17.6 en `C:\Program Files\PostgreSQL\17\bin` (no están en `PATH`).
- Java 21.0.6.

## 2. Estado de etapas cerradas

| Etapa | Estado constatado | Decisión |
|---|---|---|
| 4.16 | 189 fuentes, autorización central por roles, 21/21 MEMORY y 21/21 POSTGRES, AppContext 12/12 | No rehacer |
| 4.17 | CU-18 implementado, 15/15 MEMORY y 15/15 POSTGRES, permisos 6/6 | No rehacer |
| 4.15 | Arneses funcionales anteriores no afectados por CU-19 | No repetir |

## 3. Hallazgos que condicionan CU-19

1. **No existe auditoría general reutilizable** en el sistema. Por tanto se crea una migración nueva `V3__backup_auditoria.sql`; V1 y V2 no se modifican y sus checksums permanecen intactos.
2. **No existe relación usuario↔UEB ni UEB predeterminada.** CU-19 no puede limitarse “a la UEB del usuario” sin inventar una regla nueva. Se aplica a nivel de instalación, sin superusuario ni roles nuevos.
3. **`CasoUso.CU_19` estaba documentado pero deshabilitado** y aparecía como fuera de alcance en la matriz; debe habilitarse con `Rol.ADMINISTRADOR` sin crear un `moduleId` nuevo: será una pestaña dentro de `administracion`.
4. **PICAN-SYNC 1.0 no prevé el bloqueo SIN-13 completo.** El DCUS (líneas 406 y 474-477) exige impedir emisión de vales y generación de paquetes tras restaurar, y reanudar solo tras importar la bajada completa del central con `ultimo_vale_conocido` y la última secuencia del central. El protocolo actual no define esa bajada, ni `secuencia`, ni `ultimo_vale_conocido`; `DISTRIBUCION_CENTRAL` solo está diseñado, no implementado.
5. **No existe `SwingWorker` en `src`.** La UI nueva debe introducirlo para no bloquear el EDT durante `pg_dump` o `pg_restore`.
6. **`DatabaseConfig.getModo()` (`LOCAL`/`CENTRAL`) existe pero ninguna regla de negocio lo usaba.** Es el único indicador disponible para aplicar el bloqueo SIN-13 solo en instalaciones UEB, sin ampliar el modelo de datos.
7. **`Connection.getCatalog()` es la forma fiable de identificar la base activa.** `getBaseDatos()` puede divergir si se usa una URL JDBC completa; las operaciones administrativas deben derivar host, puerto y base desde la URL real y desde `getCatalog()`.
8. **La contraseña no debe aparecer nunca en argumentos visibles de `pg_dump`/`pg_restore`, manifiestos, logs ni documentos.** Debe pasar por `PGPASSWORD` en el entorno del proceso hijo, con `--no-password`, timeout y salida saneada.

## 4. Decisión de alcance

- Se implementan generación manual, inspección, restauración segura, auditoría, autorización ADMINISTRADOR y el bloqueo local SIN-13 sobre operaciones existentes.
- No se copia automáticamente cada día ni se inventa una política de retención.
- No se cambia PICAN-SYNC 1.0 ni el formato de sus paquetes.
- Queda pendiente, y se documenta como limitación, el procedimiento de bajada completa del central, el campo `ultimo_vale_conocido` y la última secuencia central. El desbloqueo automático no se simula.

## 5. Conclusión

La Fase 0 cerró sin ambigüedad sobre la arquitectura a reutilizar y sobre el límite exacto de SIN-13. No se programó antes de completar este diagnóstico.
