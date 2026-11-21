# PROGRESO — ETAPA 4.14 (PICAN · Integración PostgreSQL)

Fuente de verdad: `C:\Users\Ariel David\Desktop\bioproductos\aplicacionPican\`
Fecha de cierre: 2026-11-20.

> Registro vivo de la etapa. No sustituye a la implementación. Se actualizó al terminar cada fase.

---

## Estado general

- **Estado: ETAPA 4.14 COMPLETADA.**
- **Fases 0 a 6 completadas.**
- Trabajo previo aprobado **no repetido**: 20 repositorios JDBC validados (145/145), migración V1 aplicada/verificada (24 tablas, 29 FK, 24 triggers, índices y checksum), 24 comprobaciones transaccionales del agente anterior.
- Servidor PostgreSQL del sistema (puerto 5432) **no modificado**. Se usó el clúster temporal de pruebas `Temp\opencode\pican-pg17-fase14` en `127.0.0.1:55432`, usuario `pican_test`, autenticación `trust` de loopback.
- Ninguna credencial real en Git. La contraseña de prueba se pasó solo por `-D` en cada ejecución; los logs fueron depurados de contraseñas temporales generadas por `UsuarioInicialInitializer`.

---

## Fase 0 — Inspección y preparación (COMPLETADA)

- Confirmada ruta real y estructura (`src/cu/pican`, `persistence`, `repository/jdbc`, `repository/memory`, `sql/migrations`).
- Git revisado: HEAD `33bdf0d`; `aplicacionPican/` sin seguimiento; **no se hicieron reset, restore ni sobrescrituras**.
- Se localizaron los scripts oficiales:
  - `scripts/compilar.ps1` → `javac -encoding UTF-8 -cp "lib\*" -d out <174 fuentes>`.
  - `scripts/ejecutar.ps1` → `java -cp "out;lib\*" cu.pican.app.PicanApp` con `PICAN_PERSISTENCE=memory|postgresql`.
- Driver `lib/postgresql-42.7.13.jar` disponible; no hay Maven/Gradle.
- Inventario de escritura de servicios: 6 entry points ya transaccionados (`ValeEntregaService.anular`, `RecepcionService.registrar`, `IncidenciaService.cerrar`, `InformeService.crearRevision`, `registrarDecision`, `registrarEntrega`). No hay secuencias multi-escritura sin transacción, salvo el detalle de `UsuarioRepositoryPostgres` (rol + usuario), tratado en Fase 3.
- Se confirmó que `AppContext` ya implementaba el modo dual; **no se rehízo**.

## Fase 1 — Pruebas transaccionales (COMPLETADA)

- No se repitieron los 24 checks previos ni los 145 checks JDBC.
- Arnés nuevo: `test/cu/pican/verify/TransaccionesPostgresTest.java`, base exclusiva `pican_tx_test`.
- **10/10 aprobadas:** commit, rollback por excepción, inserción + excepción sin datos parciales, dos escrituras atómicas, fallo de FK en la segunda operación con rollback de la primera, liberación de conexiones, conexión utilizable tras rollback, operación posterior tras rollback, repositorios usando la conexión transaccional, transacciones anidadas con la misma conexión.
- Evidencia: `test-results/RESULTADOS_TRANSACCIONES.txt` y `test-results/final-TransaccionesPostgresTest.log`.

## Fase 2 — Integración en AppContext (COMPLETADA)

- Arnés: `test/cu/pican/verify/AppContextIntegrationTest.java`, base exclusiva `pican_appcontext_test`.
- **12/12 aprobadas:** MEMORY y POSTGRES correctamente seleccionados; 20 repositorios memory/Postgres; sin conexión JDBC en memoria y con `JdbcTransactionManager` en PostgreSQL; ausencia de datos `DEMO-` en PostgreSQL; 67 indicadores y 2 metas oficiales cargados de forma idempotente; administrador inicial; alta/consulta de UEB por servicios + JDBC; persistencia tras reconstruir `AppContext`; **sin fallback silencioso** (PostgreSQL inaccesible → `PersistenciaException`).
- Evidencia: `test-results/RESULTADOS_APPCONTEXT.txt` y `test-results/final-AppContextIntegrationTest.log`.

## Fase 3 — Transacciones en servicios (COMPLETADA)

- **Cambio real:** `src/cu/pican/service/UsuarioService.java` acepta `TransactionManager` mediante constructor sobrecargado; los 8 métodos de escritura participan en la transacción. El constructor histórico mantiene `NoOpTransactionManager` para demos. `AppContext` inyecta el gestor JDBC.
- **Cambio real:** `AppContext.java` pasa `transacciones` a `UsuarioService`.
- **Cambio real:** `CatalogosOperativosStageDemo.java` solo llama a `DemoDataInitializer` en modo memoria.
- Las compensaciones manuales de `RecepcionService`/`IncidenciaService` se conservan: siguen siendo necesarias en MEMORY y son inofensivas en PostgreSQL.
- Arnés: `test/cu/pican/verify/ServiciosTransaccionalesTest.java`, base exclusiva `pican_servicios_test`.
- **5/5 aprobadas:** recepción NO_CONFORME confirma vale + recepción + incidencia; fallo forzado en el segundo write revierte el estado del vale; cierre de última incidencia cierra el vale; regla de no reapertura; `UsuarioService` participa en la transacción activa (rol + usuario).
- Evidencia: `test-results/RESULTADOS_SERVICIOS.txt` y `test-results/final-ServiciosTransaccionalesTest.log`.

## Fase 4 — Pruebas funcionales PostgreSQL (COMPLETADA)

- Arnés: `test/cu/pican/verify/FuncionalPostgresTest.java`, base exclusiva `pican_funcional2_test`.
- **8/8 aprobadas:** UUID/códigos/baja lógica de UEB, nave y unidad; parte de nave consultado en análisis con cantidad exacta; carne y consolidado por período con precisión decimal; 67 referencias y 2 metas oficiales; informe con fuente, decisión y entrega; rechazo de entrega anticipada; revisión que conserva el informe anterior; persistencia del informe tras reconstruir `AppContext`.
- Demos reales sobre PostgreSQL (con `AppContext`): `CatalogosGestionIntegralStageDemo` **14/14 secciones** y `UsuarioAuthStageDemo` **finalizada correctamente**.
- Evidencia: `test-results/RESULTADOS_FUNCIONAL.txt`, `test-results/fase4-catalogos.log`, `test-results/fase4-usuarios.log`, `test-results/final-FuncionalPostgresTest.log`.

## Fase 5 — Regresión MEMORY (COMPLETADA)

- **14/14 demos automatizadas terminan con código 0**: RepositoryDemo, ServiceDemo, EggProductionStageDemo, MeatProductionStageDemo, ConsolidationStageDemo, CatalogosOperativosStageDemo, ValeEntregaStageDemo, RecepcionStageDemo, IncidenciaStageDemo, ValeAnulacionStageDemo, AnalysisStageDemo, InformeStageDemo, UsuarioAuthStageDemo, CatalogosGestionIntegralStageDemo.
- Correcciones aplicadas **solo a datos de prueba**, sin cambiar reglas de negocio:
  - Se completaron los campos obligatorios de `ParteNave` en AnalysisStageDemo, InformeStageDemo, UsuarioAuthStageDemo y CatalogosGestionIntegralStageDemo (validación `P2` heredada).
  - `AnalysisStageDemo` inserta directamente en el repositorio el parte con `huevosProducidos = null` para preservar el caso “sin dato” que el servicio ya no admite.
  - `CatalogosGestionIntegralStageDemo` comprueba ausencia de datos `DEMO-` en PostgreSQL en lugar de exigir catálogos vacíos (con persistencia real los catálogos contienen lo ya grabado por el propio demo).
- Evidencia: `test-results/fase5-*.log`.

## Fase 6 — Documentación y cierre (COMPLETADA)

- Este archivo y `INFORME_FINAL_ETAPA_4.14.md`.
- Compilación oficial final: `.\scripts\compilar.ps1` → **OK: 174 fuentes compiladas en out**.
- Arnés compilados con `javac -encoding UTF-8 -cp lib\postgresql-42.7.13.jar -d out-test <src + test>`.

---

## Comandos de reproducción

```powershell
# Desde aplicacionPican\
.\scripts\compilar.ps1

$fuentes = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
$pruebas = @(Get-ChildItem test -Recurse -Filter *.java | ForEach-Object FullName)
javac -encoding UTF-8 -cp 'lib\postgresql-42.7.13.jar' -d out-test ($fuentes + $pruebas)

$cp = 'out-test;lib\postgresql-42.7.13.jar'
$comunes = @('-Dpican.test.user=pican_test',
             '-Dpican.test.password=<SOLO_PARA_CLUSTER_DE_PRUEBAS>',
             '-Dpican.test.migrations=sql/migrations')

java $comunes '-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_tx_test'            -cp $cp cu.pican.verify.TransaccionesPostgresTest
java $comunes '-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_appcontext_test'    -cp $cp cu.pican.verify.AppContextIntegrationTest
java $comunes '-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_servicios_test'     -cp $cp cu.pican.verify.ServiciosTransaccionalesTest
java $comunes '-Dpican.test.url=jdbc:postgresql://127.0.0.1:55432/pican_funcional2_test'    -cp $cp cu.pican.verify.FuncionalPostgresTest

# Regresión MEMORY
$env:PICAN_PERSISTENCE='memory'
java -cp 'out;lib\*' cu.pican.app.CatalogosGestionIntegralStageDemo
# ...y el resto de StageDemo/RepositoryDemo/ServiceDemo
```

Cada arnés exige que el nombre de la base contenga `test` o `prueba`; rechaza cualquier otra base.

---

## Archivos creados en esta continuación

- `PROGRESO_ETAPA_4.14.md`, `INFORME_FINAL_ETAPA_4.14.md`.
- `test/cu/pican/verify/TransaccionesPostgresTest.java`.
- `test/cu/pican/verify/AppContextIntegrationTest.java`.
- `test/cu/pican/verify/ServiciosTransaccionalesTest.java`.
- `test/cu/pican/verify/FuncionalPostgresTest.java`.
- `test-results/*.txt`, `test-results/*.log` (evidencia, sin secretos).

## Archivos modificados en esta continuación

- `src/cu/pican/service/UsuarioService.java` (transaccionalidad).
- `src/cu/pican/app/AppContext.java` (inyección del gestor transaccional).
- `src/cu/pican/app/CatalogosOperativosStageDemo.java` (demo solo en memoria).
- `src/cu/pican/app/AnalysisStageDemo.java`, `InformeStageDemo.java`, `UsuarioAuthStageDemo.java`, `CatalogosGestionIntegralStageDemo.java` (datos de prueba válidos y comportamiento dual).

## No ejecutado / fuera de alcance

- GUI manual (no se usó como bloqueante).
- CU-18, CU-19, CU-04, USB, camiones, choferes e inventario.
- Repetición de los 145 checks JDBC y de la verificación de V1 ya aprobados.

## Resultado final

| Ámbito | Resultado |
|---|---|
| Arneses nuevos (Fases 1–4) | **35/35 comprobaciones aprobadas** |
| Demos MEMORY (Fase 5) | **14/14 exitosas** |
| Demos con PostgreSQL real | **2/2 exitosas** |
| Trabajo previo aprobado | 145/145 + 24/24 + V1 verificada (no repetido) |
| Regresiones de integración | **0** |
