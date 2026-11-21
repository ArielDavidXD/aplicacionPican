# Pruebas — Etapa 4.18 / CU-19

Fecha: 2026-11-20  
Entorno: Java 21.0.6, PostgreSQL 17.6 en `127.0.0.1:55432`, usuario de prueba `pican_test`, autenticación trust del servidor temporal.  
Resultado: **aprobado con limitación documentada en SIN-13** (coincide con `INFORME_FINAL_ETAPA_4.18.md`).

Nota: en este documento `SIN-13` identifica el requisito de bloqueo tras restauración. En `PRUEBAS_ETAPA_4.15.md` el mismo texto se usó como identificador de un caso de prueba de confirmación; no son el mismo asunto.

## 1. Compilación

```powershell
.\scripts\compilar.ps1
```

Resultado: `OK: 228 fuentes compiladas en out.`

Compilación de arneses:

```powershell
javac -encoding UTF-8 -cp "out;lib\*" -d test-out (Get-ChildItem -Recurse test -Filter *.java | ForEach-Object { $_.FullName })
```

## 2. CU-19 en MEMORY

```powershell
java -cp 'out;test-out;lib\*' '-Dpican.test.admin.password=<CLAVE_ADMIN_PRUEBA>' cu.pican.verify.CopiasSeguridadTest memoria
```

**Resultado: 15/15 comprobaciones aprobadas.**

| ID | Comprobación |
|---|---|
| CU19-01 | CU-19 habilitado y reservado al ADMINISTRADOR |
| CU19-02 | Sin nuevo `moduleId`; solo se agrega a `administracion` |
| CU19-03 | `AppContext` expone `BackupService` |
| CU19-04 | Sin sesión: crear copia rechazado |
| CU19-05 | JEFE no puede crear, inspeccionar ni restaurar |
| V3-01 | Migración V3 existe y V1/V2 no se tocan |
| VAL-100-01 | Copia íntegra y compatible aceptada |
| VAL-100-02 | Alteración del volcado detectada por SHA-256 |
| VAL-100-03 | Versión futura rechazada |
| VAL-100-04 | Entrada ZIP no permitida rechazada |
| VAL-98-01 | Directorio inválido rechazado y auditado como fallo |
| VAL-98-02 | MEMORY rechaza copias reales con mensaje explícito |
| VAL-99-01 | MEMORY rechaza restauración real antes de tocar datos |
| AUD-01 | Auditoría conserva usuario, operación, archivo, fecha y resultado |
| SEG-01 | Contraseña ausente en manifiesto y auditoría |

## 3. CU-19 en POSTGRESQL real

```powershell
java -cp 'out;test-out;lib\*' `
  '-Dpican.test.admin.password=<CLAVE_ADMIN_PRUEBA>' `
  '-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_418_test' `
  '-Dpican.test.user=pican_test' `
  '-Dpican.test.password=<CLAVE_PG_PRUEBA>' `
  cu.pican.verify.CopiasSeguridadTest postgresql
```

**Resultado: 23/23 comprobaciones aprobadas.**  
El arnés creó su propia base `pican_cu19_<timestamp>_test`, ejecutó las migraciones, realizó `pg_dump`/`pg_restore` reales y eliminó esa base al terminar. No se usó ninguna base real de negocio.

| ID | Comprobación |
|---|---|
| CU19-01…05, V3-01, VAL-100-01…04, VAL-98-01 | Igual que MEMORY |
| PG-01 | Se identifica la base activa con `current_database()` y contiene `test` |
| VAL-98-03 | `pg_dump` real genera copia publicada y verificada |
| VAL-100-05 | ZIP con exactamente tres entradas y VAL-100 correcto |
| VAL-99-02 | Falta la segunda confirmación: cancelado sin sustituir datos |
| VAL-99-03 | Restauración real completada, verificada y con V3 aplicada |
| AUD-02 | Restauración exitosa registrada con hash y auditoría |
| SIN-13-01 | Tras restaurar en LOCAL se bloquea emisión de vales |
| SIN-13-02 | Tras restaurar en LOCAL se bloquea generación de paquetes |
| SIN-13-03 | En CENTRAL el mismo control no bloquea |
| REC-01 | Fallo simulado tras el intercambio: se reactiva la base anterior, se conserva la fallida y se audita el fallo |
| AUD-01, SEG-01 | Auditoría y saneamiento de credenciales |

Limpieza confirmada: `[LIMPIEZA] Base temporal pican_cu19_1795216296047_test eliminada.`

## 4. Regresiones afectadas

No se repitieron arneses funcionales de 4.15. Solo se ejecutaron los afectados por autorización, interfaz y composición de `AppContext`.

```powershell
java -cp 'out;lib\*' cu.pican.verify.PermisosInterfazTest
java -cp 'out;lib\*' cu.pican.verify.AutorizacionRolesTest memoria
java -cp 'out;lib\*' '-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_autorizacion_418_test' `
  '-Dpican.test.user=pican_test' '-Dpican.test.password=<CLAVE_PG_PRUEBA>' `
  cu.pican.verify.AutorizacionRolesTest postgresql
java -cp 'out;lib\*' '-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_appcontext_418_test' `
  '-Dpican.test.user=pican_test' '-Dpican.test.password=<CLAVE_PG_PRUEBA>' `
  cu.pican.verify.AppContextIntegrationTest
```

| Arnés | Resultado |
|---|---:|
| `PermisosInterfazTest` | 6/6 |
| `AutorizacionRolesTest` MEMORY | 21/21 |
| `AutorizacionRolesTest` POSTGRES | 21/21 |
| `AppContextIntegrationTest` | 12/12 |

Las bases temporales `pican_autorizacion_418_test` y `pican_appcontext_418_test` se eliminaron al finalizar.

**Total de esta etapa:** 15 + 23 + 6 + 21 + 21 + 12 = **98 comprobaciones aprobadas**, más la compilación oficial.

## 5. Limitaciones de la verificación

- La bajada completa del central exigida por SIN-13 no está implementada en PICAN-SYNC 1.0. Se verificó únicamente el bloqueo local; no se simuló desbloqueo.
- No se ejecutaron pruebas manuales GUI. La pestaña existe y compila, pero no se afirma validación visual manual.
- Las herramientas PostgreSQL se localizaron en la instalación 17.6; el código también admite `-Dpican.backup.pg.bin` o `PICAN_PG_BIN`.
