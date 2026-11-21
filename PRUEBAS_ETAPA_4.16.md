# PRUEBAS — ETAPA 4.16 (PICAN)

Fecha: 2026-11-20 · Ámbito: autorización integral por roles en servicios, navegación e interfaz, sin rehacer funcionalidades aprobadas en 4.15.

> Todas las ejecuciones registradas aquí se realizaron realmente. Las pruebas manuales GUI preparadas en 4.15 **no se ejecutaron** en esta etapa.

---

## 1. Resumen general

| Ámbito | Resultado | Evidencia |
|---|---|---|
| Compilación oficial | **OK · 189 fuentes en `out`** | `.\scripts\compilar.ps1` |
| AUT · MEMORY | **21/21** | `test-results/autorizacion-4.16-memory.log` |
| AUT · POSTGRES real | **21/21** | `test-results/autorizacion-4.16-postgres.log` |
| Matriz de interfaz | **6/6** | `test-results/permisos-interfaz-4.16.log` |
| Regresión `AppContext` | **12/12** | `test-results/regresion-AppContext-4.16.log` |
| Regresión validación integral | **20/20** | `test-results/regresion-Validacion-4.16.log` |
| Regresión sincronización 4.15 | **14/14** | `test-results/regresion-Sincronizacion-4.16.log` |
| Regresión servicios transaccionales | **5/5** | `test-results/regresion-Servicios-4.16.log` |

**Total automatizado de esta etapa:** 21 + 21 + 6 + 12 + 20 + 14 + 5 = **99 comprobaciones aprobadas**, más la compilación oficial.

No se repitieron los arneses 4.15 no afectados por `AppContext`, la autoridad central o la construcción de servicios.

---

## 2. Entorno

- Clúster PostgreSQL 17.6 temporal en `127.0.0.1:55432`, usuario `pican_test`, autenticación `trust`. No se tocó el servicio en `5432`.
- Bases exclusivas de pruebas, recreadas antes de las ejecuciones PostgreSQL:
  - `pican_autorizacion_416_test`
  - `pican_appcontext_416_test`
  - `pican_validacion_416_test`
  - `pican_servicios_416_test`
  - `pican_sync_ueb_a_test`, `pican_sync_ueb_b_test`, `pican_sync_central_test`
- Todas contienen `test` en el nombre. No se usaron datos empresariales reales.
- Compilación de arneses:
  ```powershell
  $fuentes = Get-ChildItem -Recurse test -Filter *.java | ForEach-Object { $_.FullName }
  javac -encoding UTF-8 -cp 'out;lib\*' -d out $fuentes
  ```
- Contraseñas de prueba suministradas por `-D` / `System.setProperty`. No aparecen en los logs de evidencia (`Select-String` sobre `test-results\*4.16*` sin coincidencias).

---

## 3. Compilación

```powershell
.\scripts\compilar.ps1
# OK: 189 fuentes compiladas en out.
```

---

## 4. AUT — Autorización por roles

Arnés: `test/cu/pican/verify/AutorizacionRolesTest.java`.

### MEMORY

```powershell
java -cp 'out;lib\*' cu.pican.verify.AutorizacionRolesTest memoria
```

### POSTGRES real

```powershell
$a = @(
  '-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_autorizacion_416_test',
  '-Dpican.test.user=pican_test',
  '-Dpican.test.password=<CLAVE_PG_PRUEBA>',
  '-cp','out;lib\*',
  'cu.pican.verify.AutorizacionRolesTest','postgresql'
)
java $a
```

### Cobertura

| ID | Comprobación |
|---|---|
| AUT-01 | ADMINISTRADOR solo CU-16 a CU-19 en la matriz central. |
| AUT-02 | JEFE_DE_UEB sin CU administrativos ni de estadística. |
| AUT-03 | ESTADÍSTICA y PRODUCCIÓN separados. |
| AUT-04 | DIRECTIVO sin cuenta ni rol posible. |
| AUT-05 | CU-18 reservado al ADMINISTRADOR; aún no implementado. |
| AUT-06 | Sin sesión: lectura de catálogo rechazada en servicio. |
| AUT-07 | Sin sesión: lectura de producción rechazada en servicio. |
| AUT-08 | Navegación directa: JEFE no entra a administración. |
| AUT-09 | Navegación directa: ESTADÍSTICA no entra a producción. |
| AUT-10 | Navegación directa: PRODUCCIÓN ve referencias, no informes. |
| AUT-11 | ADMIN no puede registrar partes (CU-05). |
| AUT-12 | ADMIN no puede emitir vales (CU-10). |
| AUT-13 | ADMIN no puede consultar análisis operativo (CU-14). |
| AUT-14 | JEFE no puede gestionar usuarios (CU-16). |
| AUT-15 | JEFE no puede elaborar informes (CU-15). |
| AUT-16 | JEFE sí consulta sus datos de producción (CU-05). |
| AUT-17 | ESTADÍSTICA no puede confirmar recepciones (CU-11). |
| AUT-18 | ESTADÍSTICA sí consulta referencias oficiales (CU-14). |
| AUT-19 | PRODUCCIÓN no puede modificar catálogos (CU-17). |
| AUT-20 | PRODUCCIÓN sí consulta referencias (CU-01 a CU-03). |
| AUT-21 | Rechazo por rol sin escrituras parciales. |

**Resultado: 21/21 en ambos modos.**

---

## 5. Matriz de interfaz

Arnés: `test/cu/pican/verify/PermisosInterfazTest.java`.

```powershell
java -cp 'out;lib\*' cu.pican.verify.PermisosInterfazTest
```

**Resultado: 6/6 comprobaciones aprobadas.** La matriz de UI se deriva de `CasoUso`, no de una lista paralela.

---

## 6. Regresiones 4.15 realmente afectadas

Se incluyen solo porque 4.16 tocó `AppContext`, la autoridad central, la navegación o la construcción de servicios.

| Arnés | Resultado | Ajuste realizado en el arnés |
|---|---|---|
| `AppContextIntegrationTest` | 12/12 | Login administrativo antes de siembras y CRUD; `DemoDataInitializer` usa autoridad temporal de demo y restaura la real. |
| `ValidacionIntegralPostgresTest` | 20/20 | Cuentas creadas bajo CU-16 y cambio de sesión por rol según el CU ejercitado. |
| `SincronizacionPostgresTest` | 14/14 | Altas de cuentas con sesión administrativa; siembra operativa sin vínculo usuario↔UEB mediante política de demo explícita y restaurada. |
| `ServiciosTransaccionalesTest` | 5/5 | Sin cambios funcionales; confirma que la base `ServicioAutorizado` no alteró transacciones. |

Comandos representativos:

```powershell
$a = @('-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_appcontext_416_test',
       '-Dpican.test.user=pican_test','-Dpican.test.password=<CLAVE_PG_PRUEBA>',
       '-cp','out;lib\*','cu.pican.verify.AppContextIntegrationTest')
java $a
```

Las bases de sincronización se recrearon antes de la ejecución final; si se reutilizan sin recrear, el arnés puede detectar conflictos de versiones de ejecuciones anteriores, como ocurría en 4.15.

---

## 7. Lo que no se probó ni se afirma

- No se ejecutaron pruebas manuales GUI.
- No se probó Apache POI ni CU-18: no hay plantillas oficiales disponibles y su implementación corresponde a 4.17.
- No se declaró implementada la restricción "Jefe de UEB solo en su UEB": no existe relación usuario↔UEB.
- No se verificaron CU-04 ni CU-19: fuera de alcance.

---

## 8. Cierre de evidencias

Los siete archivos `test-results/*4.16*.log` contienen las salidas reales de compilación/ejecución sin credenciales. Junto con `MATRIZ_PERMISOS_PICAN.md` y el `INFORME_FINAL_ETAPA_4.16.md`, sustentan el cierre de la Etapa 4.16.
