# PRUEBAS — ETAPA 4.17 (CU-18 Importar datos desde Excel)

Fecha: 2026-11-20 · Proyecto: `aplicacionPican` · Resultado: **aprobado**.

---

## 1. Resumen

| Arnés | MEMORY | POSTGRES real | Evidencia |
|---|---:|---:|---|
| `ImportacionExcelTest` (CU-18) | **15/15** | **15/15** | `test-results/importacion-4.17-memory.log`, `test-results/importacion-4.17-postgres.log` |
| `AutorizacionRolesTest` (regresión 4.16 afectada) | **21/21** | **21/21** | `test-results/regresion-Autorizacion-4.17-memory.log`, `test-results/regresion-Autorizacion-4.17-postgres.log` |
| `AppContextIntegrationTest` (regresión afectada) | **12/12** (MEM+PG en una ejecución) | incluido | `test-results/regresion-AppContext-4.17.log` |
| `PermisosInterfazTest` (regresión afectada) | **6/6** | no aplica | `test-results/regresion-Permisos-4.17.log` |

**Total automatizado de esta etapa:** 15 + 15 + 21 + 21 + 12 + 6 = **90 comprobaciones aprobadas**, más la compilación oficial.

No se repitieron arneses 4.15 no afectados (`ValidacionIntegralPostgresTest`, `SincronizacionPostgresTest`, `ServiciosTransaccionalesTest`, `TransaccionesPostgresTest`). `PICAN-SYNC 1.0` no se modificó.

---

## 2. Entorno

- Java: `openjdk version "21.0.6"`, Temurin LTS.
- PostgreSQL temporal 17.6 en `127.0.0.1:55432`, usuario `pican_test`, autenticación `trust`. No se tocó el puerto `5432`.
- Bases exclusivas de prueba, recreadas antes de ejecutar; todas contienen `test` en el nombre:
  - `pican_importacion_417_test`
  - `pican_appcontext_417_test`
  - `pican_autorizacion_417_test`
- Contraseñas de prueba suministradas únicamente por `-D` (`-Dpican.test.admin.password`, `-Dpican.test.password`). `AppContextIntegrationTest` mantiene su literal histórico de 4.16, pero no se imprime en sus evidencias.
- Archivos `.xlsx` se generan en un directorio temporal `pican-importacion-test-*` dentro de la prueba y se eliminan al terminar. No hay fixtures binarios versionados.
- `lib/` sigue conteniendo solo `postgresql-42.7.13.jar`; **no se añadió Apache POI**.
- `Select-String` sobre `test-results\*4.17*` para los literales de credenciales de prueba (aquí documentados solo como `<CLAVE_ADMIN_PRUEBA>`, `<CLAVE_PG_PRUEBA>`, `<CLAVE_APP_PRUEBA>`), además de `password` y `contrase`: **sin coincidencias**.

---

## 3. Compilación

```powershell
.\scripts\compilar.ps1
# OK: 204 fuentes compiladas en out.

$fuentes = Get-ChildItem -Recurse test -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -cp 'out;lib\*' -d out $fuentes
# OK: 10 fuentes de prueba compiladas.
```

---

## 4. Preparación de bases PostgreSQL

```powershell
$env:PGPASSWORD='<CLAVE_PG_PRUEBA>'
& 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -h 127.0.0.1 -p 55432 -U pican_test -d postgres `
  -c 'DROP DATABASE IF EXISTS pican_importacion_417_test;'
& 'C:\Program Files\PostgreSQL\17\bin\createdb.exe' -h 127.0.0.1 -p 55432 -U pican_test pican_importacion_417_test
```

Se repitió el mismo patrón para `pican_appcontext_417_test` y `pican_autorizacion_417_test`.

---

## 5. CU-18 — Importación Excel

Arnés: `test/cu/pican/verify/ImportacionExcelTest.java`.

### MEMORY

```powershell
$a = @('-Dpican.test.admin.password=<CLAVE_ADMIN_PRUEBA>','-cp','out;lib\*',
       'cu.pican.verify.ImportacionExcelTest','memoria')
java $a 2>&1 | Tee-Object -FilePath 'test-results\importacion-4.17-memory.log'
# === RESULTADO MEMORIA: 15/15 APROBADAS ===
```

### POSTGRES real

```powershell
$a = @(
  '-Dpican.test.admin.password=<CLAVE_ADMIN_PRUEBA>',
  '-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_importacion_417_test',
  '-Dpican.test.user=pican_test',
  '-Dpican.test.password=<CLAVE_PG_PRUEBA>',
  '-cp','out;lib\*',
  'cu.pican.verify.ImportacionExcelTest','postgresql'
)
java $a 2>&1 | Tee-Object -FilePath 'test-results\importacion-4.17-postgres.log'
# === RESULTADO POSTGRESQL: 15/15 APROBADAS ===
```

### Cobertura

| ID | Comprobación |
|---|---|
| IMP-01 | CU-18 implementado y reservado al ADMINISTRADOR en la matriz. |
| IMP-02 | Sin sesión: `preparar` rechazado con `AutorizacionException`. |
| IMP-03 | JEFE no puede preparar ni confirmar. |
| IMP-04 | ADMIN ve el servicio en `AppContext`; sigue sin permisos de CU-05. |
| IMP-05 | Encabezados incorrectos: error estructural, detiene sin escribir. |
| IMP-06 | Extensión distinta de `.xlsx` rechazada. |
| IMP-07 | ZIP inválido rechazado. |
| IMP-08 | Entrada ZIP con `../` rechazada. |
| IMP-09 | Filas válidas, rechazadas y repetidas clasificadas por número de fila. |
| IMP-10 | Vista previa no escribió nada. |
| IMP-11 | Confirmación cargó solo las filas válidas en MEMORY/POSTGRES. |
| IMP-12 | Coma decimal de la Tabla 4 aceptada sin transformar el valor. |
| IMP-13 | Sesión cerrada tras la vista previa impide confirmar; sin escritura. |
| IMP-14 | Servicio directo rechaza a JEFE en `preparar` y `confirmar`. |
| IMP-15 | Sin sesión, el servicio directo falla cerrado. |

---

## 6. Regresiones afectadas

```powershell
java -cp 'out;lib\*' cu.pican.verify.PermisosInterfazTest `
  2>&1 | Tee-Object -FilePath 'test-results\regresion-Permisos-4.17.log'

$a = @('-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_appcontext_417_test',
       '-Dpican.test.user=pican_test','-Dpican.test.password=<CLAVE_PG_PRUEBA>',
       '-cp','out;lib\*','cu.pican.verify.AppContextIntegrationTest')
java $a 2>&1 | Tee-Object -FilePath 'test-results\regresion-AppContext-4.17.log'
```

`AutorizacionRolesTest` se ejecutó en MEMORY y en POSTGRES con el patrón de la Etapa 4.16, usando `pican_autorizacion_417_test`. Se actualizó únicamente el texto de AUT-05 para indicar que CU-18 ya está implementado; la aserción no cambió.

---

## 7. Límites de la evidencia

- No se ejecutaron pruebas manuales GUI; la pestaña nueva quedó disponible para revisión posterior.
- No se entregaron plantillas `.xlsx` físicas de PICAN. La compatibilidad queda demostrada contra la estructura normalizada derivada de las entidades y de `ReferenciasHistoricasInitializer`, no contra archivos reales no proporcionados.
- No se implementaron \"tipos de datos operativos\" porque el DCUS no los define.
- No se modificaron `cu.pican.sync`, migraciones SQL, catálogos, vales, recepciones, incidencias ni informes.
