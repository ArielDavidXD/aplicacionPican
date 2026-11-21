# INFORME FINAL — ETAPA 4.15 (PICAN)

**Fecha:** 2026-11-20  
**Ubicación:** `C:\Users\Ariel David\Desktop\bioproductos\aplicacionPican\`  
**Objetivo:** validación integral con PostgreSQL e implementación verificable de la sincronización manual UEB↔Dirección de Producción mediante paquetes transportados en USB.

---

## 1. Conclusión ejecutiva

La Etapa 4.15 quedó **completada y verificada**. Se integró un motor de sincronización real sobre PostgreSQL, se conectó con la interfaz Swing, se comprobó con tres bases temporales independientes (UEB A, UEB B y central) y se conservó la operación del modo MEMORY para demos.

**No se declaró completa la seguridad por roles de toda la aplicación**: la autorización se implementó y verificó en el módulo de sincronización y en la navegación, pero los servicios de negocio preexistentes siguen sin comprobaciones de rol. Eso se documenta explícitamente en `MATRIZ_PERMISOS_PICAN.md`.

---

## 2. Cumplimiento por fases

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Inspección, matriz de 24 tablas y diagnóstico de permisos | Completada |
| 1 | Validación integral PostgreSQL (20/20), sin repetir 145 checks JDBC | Completada |
| 2 | Protocolo `PICAN-SYNC 1.0`, formato ZIP/JSON, reglas y migración V2 | Completada |
| 3 | Motor `cu.pican.sync`: archivo, JSON, esquema, repositorio, servicio | Completada |
| 4 | Integración `AppContext`, filtro de menú, defensa en navegación y panel real | Completada |
| 5 | Pruebas exhaustivas de sincronización (14/14) | Completada |
| 6 | Regresión (12/12 + 5/5 + 8/8 + 10/10 + 14 demos MEMORY) | Completada |
| 7 | Manual operativo y documentación de pruebas | Completada |
| 8 | Informe final y cierre del registro de progreso | Completada |

---

## 3. Entregables

### Documentación
- `PROGRESO_ETAPA_4.15.md` — registro vivo, cerrado en Fase 8.
- `MATRIZ_PERMISOS_PICAN.md` — permisos reales, implementados y pendientes.
- `DISEÑO_SINCRONIZACION_PICAN.md` — protocolo y decisiones técnicas/pendientes.
- `MANUAL_SINCRONIZACION_PICAN.md` — procedimiento operativo por rol y USB.
- `PRUEBAS_ETAPA_4.15.md` — evidencias consolidadas.
- `PRUEBAS_MANUALES_GUI_4.15.md` — lista GUI preparada, **no ejecutada**.
- `INFORME_FINAL_ETAPA_4.15.md` — este documento.

### Código y esquema
- `sql/migrations/V2__sincronizacion.sql` — cuatro tablas de control; V1 no se modifica.
- `src/cu/pican/sync/` — `SyncException`, `SyncJson`, `SyncModelo`, `SyncArchivo`, `SyncEsquema`, `SyncRepositorio`, `SyncService`.
- `src/cu/pican/app/AppContext.java` — `getSyncService()` solo PostgreSQL; MEMORY falla con mensaje claro.
- `src/cu/pican/view/PermisosInterfaz.java` — matriz ejecutable de módulos por rol.
- `src/cu/pican/controller/NavigationController.java` — bloqueo de navegación directa no autorizada.
- `src/cu/pican/view/SideMenuPanel.java`, `MainFrame.java` — menú filtrado por rol.
- `src/cu/pican/view/modules/SyncModulePanel.java` — acciones reales CU-08/CU-09 y visualización de historial/conflictos.
- `src/cu/pican/view/components/AppButton.java` — constructor con `ActionListener`.

### Arneces
- `test/cu/pican/verify/ValidacionIntegralPostgresTest.java` (Fase 1).
- `test/cu/pican/verify/SincronizacionPostgresTest.java` + `SyncTestSupport.java` (Fase 5).
- `test/cu/pican/verify/PermisosInterfazTest.java` (Fase 4).
- Arneses de regresión 4.14 reutilizados (Fase 6).

---

## 4. Decisiones técnicas aplicadas

1. **Sin dependencias nuevas:** ZIP y JSON propios sobre JDK; `lib/` solo contiene el driver PostgreSQL.
2. **Sin fallback silencioso:** MEMORY no simula sincronización; PostgreSQL es obligatorio para este módulo.
3. **V1 intacta:** todos los controles nuevos viven en V2. No se duplicaron columnas de metadatos.
4. **Metadatos reutilizados:** `id_ueb_origen`, `version`, `creado_en`, `actualizado_en`, `sincronizado_en`, `id_lote_sincronizacion`, `eliminado_logico`.
5. **Idempotencia:** paquete repetido se omite; versión igual con contenido igual se omite; versión menor o igual distinta es conflicto; versión mayor se aplica salvo conflicto pendiente.
6. **Sin resolución automática:** conflictos quedan `PENDIENTE` con ambas versiones; no hay "último generado gana" ni borrado físico.
7. **Integridad, no autenticidad:** SHA-256 detecta corrupción; la firma digital sigue pendiente si se requiere autenticidad del emisor.
8. **Rechazos de seguridad de archivo:** versión incompatible, destino incorrecto, Zip Slip, entradas no whitelist, archivos incompletos y tamaño/registros excedidos.
9. **Seguridad de datos:** los paquetes nunca transportan contraseñas, usuarios, roles ni privilegios. Un `id_usuario` ausente en la central rechaza la fila por dependencia.
10. **Autorización en dos capas:** `SyncService` comprueba rol antes de operar; la UI filtra menú y navegación. ADMINISTRADOR y ESPECIALISTA_DE_PRODUCCION quedan fuera de sincronización.

### Correcciones realizadas durante la implementación

- Normalización de fechas ISO a `LocalDateTime` antes de `setTimestamp` (afectaba generación e importación).
- Eliminación de doble `executeUpdate()` en los preparadores de `SyncRepositorio`.
- Aceptación de ítems `GENERADO` al cerrar la confirmación en la UEB de origen.

---

## 5. Verificación real

| Prueba | Resultado |
|---|---|
| Compilación oficial (`scripts/compilar.ps1`) | OK · 182 fuentes en `out` |
| Validación integral Fase 1 | 20/20 |
| Matriz de módulos por rol | 6/6 |
| Sincronización UEB A + UEB B + central | 14/14 |
| Regresión `AppContext` | 12/12 |
| Regresión servicios | 5/5 |
| Regresión funcional PostgreSQL | 8/8 |
| Regresión transaccional | 10/10 |
| Demos MEMORY | 14/14 ejecutadas sin error |

Detalle y comandos: `PRUEBAS_ETAPA_4.15.md`. Evidencias: `test-results/*.log` y `test-results/RESULTADOS_*.txt`.

**Pruebas manuales GUI:** quedaron preparadas en `PRUEBAS_MANUALES_GUI_4.15.md` y **no se ejecutaron**. No se afirma su aprobación.

---

## 6. Estado de la seguridad por roles

| Elemento | Estado real |
|---|---|
| Autenticación PBKDF2 y sesión única | Implementada y verificada en etapas previas. |
| Autorización dentro de `SyncService` | Implementada y verificada (SIN-07). |
| Filtro de menú y navegación | Implementado y verificado (6/6). |
| ADMINISTRADOR sin permisos de sincronización | Verificado. |
| Jefe limitado "a su UEB" | **Pendiente**: no existe relación usuario↔UEB. |
| Permisos en servicios de negocio preexistentes | **Pendiente**: no se extendió esta etapa a producción, consolidación, informes ni administración. |
| Paquetes recibidos modificando seguridad | Imposible por diseño: tablas `rol`/`usuario` no son exportables y se verificó en SIN-04. |

---

## 7. Pendientes y fuera de alcance

No implementados por decisión documentada:

- Autoridad de escritura por tabla (25 UEB + central).
- Resolución de conflictos y reglas de precedencia empresariales.
- Relación usuario↔UEB y vínculo de las 28 unidades no estatales con las UEB.
- Distribución central→UEB de catálogos o históricos oficiales.
- Frecuencia de intercambio y ventanas de corte.
- Firma digital / autenticidad del emisor.
- CU-18, CU-19, CU-04, inventario, móvil, Internet, VPN, replicación y tiempo real.

---

## 8. Control de cambios

- Copia de seguridad previa: `C:\Users\Ariel David\AppData\Local\Temp\opencode\pican-etapa-4.15-backup-20261120-083718`.
- Repositorio Git raíz: `C:/Users/Ariel David/Desktop/bioproductos`, HEAD `33bdf0d`. `aplicacionPican/` continúa sin seguimiento.
- No se hicieron commits, push ni cambios de historial.
- No se usaron credenciales reales: contraseñas solo por variables de entorno o `-D`; logs de evidencia filtrados.
- No se modificó `parcial2`, frontend Vite/React/PocketBase ni `Modelo Caso de uso de Negocio\aplicacionPican\`.

---

## 9. Cierre

La Etapa 4.15 cumple su objetivo técnico: hay un protocolo versionado, un motor persistente, una integración real con la aplicación y evidencia ejecutable de idempotencia, integridad, conflictos, autorización y confirmación. Las decisiones empresariales pendientes se dejaron documentadas, no inventadas.
