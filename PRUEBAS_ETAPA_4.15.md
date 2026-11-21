# PRUEBAS — ETAPA 4.15 (PICAN)

Fecha: 2026-11-20 · Ámbito: validación integral PostgreSQL e implementación verificable de sincronización manual UEB↔central por USB.

> Todas las ejecuciones registradas aquí se realizaron realmente. La lista de pruebas **manuales GUI** (`PRUEBAS_MANUALES_GUI_4.15.md`) quedó preparada pero **no ejecutada**: no se afirma lo contrario.

---

## 1. Resumen general

| Ámbito | Resultado | Evidencia |
|---|---|---|
| Compilación oficial | **OK · 182 fuentes en `out`** | `.\scripts\compilar.ps1` |
| Fase 1 · Validación integral PostgreSQL | **20/20** | `test-results/integral-4.15.log`, `RESULTADOS_INTEGRAL_4.15.txt` |
| Fase 4 · Integración AppContext + UI + matriz de roles | **6/6** | `test-results/permisos-interfaz-4.15.log` |
| Fase 5 · Sincronización UEB A, UEB B y central | **14/14** | `test-results/sincronizacion-4.15.log`, `RESULTADOS_SINCRONIZACION_4.15.txt` |
| Fase 6 · Regresión `AppContext` | **12/12** | `test-results/regresion-AppContextIntegrationTest.log`, `RESULTADOS_APPCONTEXT.txt` |
| Fase 6 · Regresión servicios | **5/5** | `test-results/regresion-ServiciosTransaccionalesTest.log`, `RESULTADOS_SERVICIOS.txt` |
| Fase 6 · Regresión funcional PostgreSQL | **8/8** | `test-results/regresion-FuncionalPostgresTest.log`, `RESULTADOS_FUNCIONAL.txt` |
| Fase 6 · Regresión transaccional | **10/10** | `test-results/regresion-TransaccionesPostgresTest.log`, `RESULTADOS_TRANSACCIONES.txt` |
| Fase 6 · Demos MEMORY | **14/14 ejecutadas sin error** | `test-results/demo-memoria-*.log` |

**Total automatizado de esta etapa:** 20 + 6 + 14 + 12 + 5 + 8 + 10 + 14 = **89 ejecuciones/comprobaciones aprobadas**, más la compilación oficial.

No se repitieron los 145 checks JDBC de 4.14 ni la validación V1 porque los repositorios y migraciones V1 no fueron modificados. Sí se repitieron `AppContext`, servicios, funcional PostgreSQL, transacciones y demos MEMORY porque la integración del `SyncService`, el filtrado de navegación y el nuevo repositorio transaccional tocan esos puntos de entrada.

---

## 2. Entorno

- Clúster PostgreSQL 17.6 temporal en `127.0.0.1:55432`, usuario `pican_test`, autenticación `trust`. No se tocó el servicio en `5432`.
- Bases exclusivas de pruebas, recreadas antes de cada ejecución:
  - `pican_integracion_415_test` (Fase 1).
  - `pican_sync_ueb_a_test`, `pican_sync_ueb_b_test`, `pican_sync_central_test` (Fases 5 y 6 del módulo).
  - `pican_regresion_415_test` (regresión).
- Todas contienen `test` en el nombre. No se usaron datos empresariales reales.
- Compilación de arneses: `javac -encoding UTF-8 -cp "lib\*" -d out-test <fuentes>`.
- Las contraseñas se pasaron por `-D` y las líneas de contraseña temporal generadas por `UsuarioInicialInitializer` se filtraron de los archivos de evidencia. No hay credenciales reales en Git ni en los logs.

---

## 3. Fase 1 — Validación integral

Arnés: `test/cu/pican/verify/ValidacionIntegralPostgresTest.java`.

```powershell
java -Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_integracion_415_test `
     -Dpican.test.user=pican_test -Dpican.test.password=<CLAVE_PG_PRUEBA> `
     -Dpican.test.migrations=sql/migrations `
     -cp "out-test;lib\*" cu.pican.verify.ValidacionIntegralPostgresTest
```

Cobertura: arranque/migraciones/login, catálogos, producción, consolidación, vales/recepciones/incidencias/anulaciones, análisis/informes/usuarios y persistencia tras reconstruir `AppContext`.

**Resultado: 20/20 aprobadas.** No se repitieron los 145 checks JDBC ni las pruebas transaccionales previas.

---

## 4. Fase 4 — Integración y matriz de roles

Se integró `SyncService` en `AppContext` (solo PostgreSQL; MEMORY lanza `SyncException`), se filtraron los módulos del menú y se añadió una segunda barrera en `NavigationController.showModule`. `SyncModulePanel` dejó de ser un simulacro: sus acciones delegan en el servicio.

Arnés headless: `test/cu/pican/verify/PermisosInterfazTest.java`.

| Caso | Resultado |
|---|---|
| ADMINISTRADOR solo ve inicio y administración | Aprobado |
| ADMINISTRADOR no entra a sincronización | Aprobado |
| JEFE_DE_UEB ve producción y sincronización; no ve informes ni administración | Aprobado |
| ESTADISTICA ve informes y sincronización; no ve producción de huevos | Aprobado |
| ESPECIALISTA_DE_PRODUCCION solo ve inicio en la UI actual | Aprobado |
| Constructor heredado sin sesión conserva compatibilidad | Aprobado |

**Resultado: 6/6 aprobadas.** La autorización de operaciones se comprueba además en `SyncService` (ver SIN-07).

---

## 5. Fase 5 — Sincronización real

Arnés: `test/cu/pican/verify/SincronizacionPostgresTest.java`, con soporte en `SyncTestSupport.java`.

```powershell
java -cp "out-test;lib\*" cu.pican.verify.SincronizacionPostgresTest
```

| ID | Caso | Resultado |
|---|---|---|
| SIN-01 | Tres contextos aislados, autenticación por rol | Aprobada |
| SIN-02 | Datos creados sin sesión (sin vínculo usuario↔UEB) | Aprobada |
| SIN-03 | ZIP con manifiesto, SHA-256 y lote `GENERADO`; sin marcha anticipada | Aprobada |
| SIN-04 | Validación en central; sin `usuario`/`rol` en el payload | Aprobada |
| SIN-05 | Importación transaccional; metadatos de origen y lote | Aprobada |
| SIN-06 | Idempotencia: paquete repetido `OMITIDO`, sin duplicados | Aprobada |
| SIN-07 | Servicios rechazan a Jefe importando, estadística exportando y administrador consultando | Aprobada |
| SIN-08 | Corrupción del contenido rechazada por hash | Aprobada |
| SIN-09 | Versión incompatible y destino incorrecto rechazados | Aprobada |
| SIN-10 | Zip Slip y paquete incompleto rechazados | Aprobada |
| SIN-11 | Conflicto por versión: evidencia `PENDIENTE`, versión local intacta | Aprobada |
| SIN-12 | Segunda UEB importada sin mezclar `id_ueb_origen` | Aprobada |
| SIN-13 (ID de prueba 4.15) | Confirmación: `sincronizado_en`, lote y control de exportación; no se reexporta | Aprobada |
| SIN-14 | MEMORY rechaza sincronizar sin simularla | Aprobada |

**Resultado: 14/14 aprobadas.**

### Incidentes técnicos corregidos durante la fase (con justificación)

1. **Fechas ISO vs `Timestamp.valueOf(String)`:** el motor recibía `2026-...T...` y usaba el overload de cadena, que exige espacio. Se normalizó a `LocalDateTime` antes de `setTimestamp` en `SyncEsquema` y `SyncRepositorio`. Afectaba a generación, validación e importación.
2. **Doble ejecución SQL:** los `Preparador` de `SyncRepositorio` ejecutaban `ps.executeUpdate()` y el helper `ejecutar` lo ejecutaba otra vez. Se eliminó la duplicación; producía claves duplicadas en `sync_paquete`. Afectaba a todo el historial de sincronización.
3. **Cierre de confirmación:** los ítems de la UEB de origen quedan como `GENERADO`, no `APLICADO`; `confirmarPaquete` ahora los acepta. Sin esto, la confirmación se importaba pero no marcaba `sincronizado_en`.

---

## 6. Fase 6 — Regresión

| Arnés | Resultado | Justificación |
|---|---|---|
| `AppContextIntegrationTest` | 12/12 | Se añadió el `SyncService` al contexto y se modificó la rama MEMORY. |
| `ServiciosTransaccionalesTest` | 5/5 | El contexto cablea servicios y transacciones. |
| `FuncionalPostgresTest` | 8/8 | Comprueba reconstrucción de `AppContext` e informes. |
| `TransaccionesPostgresTest` | 10/10 | El nuevo repositorio participa del mismo `JdbcTransactionManager`. |
| 14 demos MEMORY | 14/14 | Verifica que el modo MEMORY sigue funcionando para demos. |

Comando representativo:

```powershell
java -Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_regresion_415_test `
     -Dpican.test.user=pican_test -Dpican.test.password=<CLAVE_PG_PRUEBA> `
     -Dpican.test.migrations=sql/migrations `
     -cp "out-test;lib\*" cu.pican.verify.AppContextIntegrationTest
```

---

## 7. Pruebas manuales pendientes

`PRUEBAS_MANUALES_GUI_4.15.md` contiene la lista para verificar en pantalla: visibilidad del menú por rol, botones de sincronización, selección de archivos USB, mensajes de rechazo y pestaña de conflictos.

**Estado: no ejecutadas.** No se sustituyen por las pruebas automáticas ni se afirma que pasaron.
