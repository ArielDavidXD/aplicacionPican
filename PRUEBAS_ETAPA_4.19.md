# PRUEBAS — ETAPA 4.19

Fecha: 2026-11-20 · Entorno: Java 21.0.6, PostgreSQL 17.6 en `127.0.0.1:55432`, usuario de prueba `pican_test`, autenticación trust del servidor temporal.  
Resultado: **125/125 comprobaciones ejecutadas aprobadas** (19 nuevas + 106 de regresión afectada).

## 1. Convención de credenciales

- Contraseña PostgreSQL: `<CLAVE_PG_PRUEBA>`, entregada por `PGPASSWORD` o `-Dpican.test.password`.
- Contraseña administrativa: `<CLAVE_ADMIN_PRUEBA>`, entregada por `-Dpican.test.admin.password` o `PICAN_ADMIN_PASSWORD`.
- Contraseña de usuarios operativos de prueba: `<CLAVE_APP_PRUEBA>`, entregada por `-Dpican.test.app.password`.
- Ningún documento de esta etapa contiene literales de contraseña. El literal histórico de `test/cu/pican/verify/AppContextIntegrationTest.java:33` se conserva para no repetir ni alterar evidencia aprobada.

## 2. Compilación

```powershell
$fuentes = Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName }
javac -d out -encoding UTF-8 $fuentes
# OK: 228 fuentes compiladas en out.

$tests = Get-ChildItem -Recurse test -Filter *.java | ForEach-Object { $_.FullName }
javac -cp out -d test-out -encoding UTF-8 $tests
# OK: 12 arneses compilados.
```

## 3. Arnés nuevo `Estabilizacion419Test`

Ubicación: `test/cu/pican/verify/Estabilizacion419Test.java`.  
Ejecuta primero comprobaciones MEMORY (D-02, D-03) y después crea una base exclusiva `pican_estabilidad_419_<timestamp>_test`, ejecuta D-01, D-07 y D-06, y la elimina con `DROP DATABASE ... WITH (FORCE)`.

```powershell
$env:PGPASSWORD='<CLAVE_PG_PRUEBA>'
java -cp "out;test-out;lib\*" `
  "-Dpican.test.admin.password=<CLAVE_ADMIN_PRUEBA>" `
  "-Dpican.test.app.password=<CLAVE_APP_PRUEBA>" `
  "-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_estabilidad_419_test" `
  "-Dpican.test.user=pican_test" `
  "-Dpican.test.password=<CLAVE_PG_PRUEBA>" `
  cu.pican.verify.Estabilizacion419Test 2>&1 |
  Tee-Object -FilePath 'test-results\PRUEBAS_ETAPA_4.19_estabilizacion.log'
```

La URL indicada solo aporta el prefijo de conexión; el arnés crea su propia base con `test` en el nombre.

### Resultados

| ID | Comprobación | Resultado |
|---|---|---|
| D-02.a | `asignarRol` rechaza quitar el rol al único administrador | Aprobada |
| D-02.b | La cuenta conserva su rol tras el rechazo | Aprobada |
| D-02.c | `actualizarDatos` aplica la misma guarda | Aprobada |
| D-02.d | Con otro administrador activo el cambio sí se permite | Aprobada |
| D-03.a | Sin credencial configurada, PostgreSQL falla cerrado | Aprobada |
| D-03.b | No se crea cuenta sin credencial | Aprobada |
| D-03.c | No se imprime contraseña generada | Aprobada |
| D-03.d | Con credencial configurada se crea la cuenta | Aprobada |
| D-03.e | La credencial configurada no aparece en consola | Aprobada |
| D-01.a | Importación devuelve `RECHAZADO` | Aprobada |
| D-01.b | Rechazo no escribe `sincronizado_en` | Aprobada |
| D-01.c | Rechazo no registra `version_confirmada` | Aprobada |
| D-01.d | Paquete de confirmación queda `RECHAZADO` | Aprobada |
| D-01.e | `IMPORTADO` sigue procesándose como `CONFIRMADO` | Aprobada |
| D-01.f | Confirmación válida sí marca `sincronizado_en` | Aprobada |
| D-07.a | Versión anterior genera exactamente un conflicto | Aprobada |
| D-07.b | Motivo original del conflicto se conserva como detalle | Aprobada |
| D-06.a | Confirmación incluye solo el conflicto de su paquete | Aprobada |
| D-06.b | El conflicto incluido corresponde al paquete indicado | Aprobada |

**Resultado del arnés: 19/19 aprobadas.** Log: `test-results/PRUEBAS_ETAPA_4.19_estabilizacion.log`.

## 4. Regresiones repetidas por cambio real

### Autorización por roles

```powershell
java -cp "out;test-out;lib\*" cu.pican.verify.AutorizacionRolesTest memoria
java -cp "out;test-out;lib\*" `
  "-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_autorizacion_419_test" `
  "-Dpican.test.user=pican_test" `
  "-Dpican.test.password=<CLAVE_PG_PRUEBA>" `
  cu.pican.verify.AutorizacionRolesTest postgresql
```

Resultado: **21/21 MEMORY** y **21/21 POSTGRES**. Logs: `test-results/regresion-Autorizacion-4.19-memory.log` y `...-postgres.log`.

### Contexto de aplicación

```powershell
java -cp "out;test-out;lib\*" `
  "-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_appcontext_419_test" `
  "-Dpican.test.user=pican_test" `
  "-Dpican.test.password=<CLAVE_PG_PRUEBA>" `
  cu.pican.verify.AppContextIntegrationTest
```

Resultado: **12/12**. Log: `test-results/regresion-AppContext-4.19.log`.

### Sincronización PostgreSQL

```powershell
$env:PGPASSWORD='<CLAVE_PG_PRUEBA>'
java -cp "out;test-out;lib\*" cu.pican.verify.SincronizacionPostgresTest
```

Resultado: **14/14**. Log: `test-results/regresion-Sincronizacion-4.19.log`.

### Copias de seguridad CU-19

```powershell
java -cp "out;test-out;lib\*" `
  "-Dpican.test.admin.password=<CLAVE_ADMIN_PRUEBA>" `
  cu.pican.verify.CopiasSeguridadTest memoria

java -cp "out;test-out;lib\*" `
  "-Dpican.test.admin.password=<CLAVE_ADMIN_PRUEBA>" `
  "-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_cu19_419_test" `
  "-Dpican.test.user=pican_test" `
  "-Dpican.test.password=<CLAVE_PG_PRUEBA>" `
  "-Dpican.test.pg.bin=C:\Program Files\PostgreSQL\17\bin" `
  cu.pican.verify.CopiasSeguridadTest postgresql
```

Resultado: **15/15 MEMORY** y **23/23 POSTGRES**. Logs: `test-results/regresion-Copias-4.19-memory.log` y `...-postgres.log`.

## 5. Evidencia reutilizada sin repetir

| Etapa | Evidencia previa | Uso |
|---|---|---|
| 4.14 | 35/35 arnés + 16 demos; 145/145 JDBC | Sin cambio que la afecte |
| 4.15 | 89 comprobaciones; solo se repitió SIN-01 a SIN-14 por cambios en sincronización | Resto reutilizado |
| 4.16 | 99 comprobaciones; se repitieron autorización y AppContext por cambios en `UsuarioService`/initializer | Resto reutilizado |
| 4.17 | 90 comprobaciones de importación | Sin cambio que la afecte |
| 4.18 | 98 comprobaciones; se repitió CU-19 por cambio en arranque/contexto | Resto reutilizado |

## 6. Limpieza

Todas las bases creadas para 4.19 se eliminaron con `DROP DATABASE IF EXISTS ... WITH (FORCE)`:

- `pican_estabilidad_419_<timestamp>_test` (creada y eliminada por el arnés nuevo en cada una de sus ejecuciones finales).
- `pican_autorizacion_419_test`, `pican_appcontext_419_test`.
- `pican_sync_ueb_a_test`, `pican_sync_ueb_b_test`, `pican_sync_central_test`.
- Base temporal propia de `CopiasSeguridadTest` (`pican_cu19_<timestamp>_test`).

Los ZIP temporales de sincronización y copia se eliminaron al finalizar cada arnés.

## 7. No probado

No se ejecutó ninguna prueba manual de Swing. La interacción real de interfaz, mensajes visuales y navegación con mouse queda documentada como **NO PROBADO** en `PRUEBAS_MANUALES_GUI_ETAPA_4.19.md`.
