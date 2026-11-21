# PRUEBAS MANUALES GUI — ETAPA 4.19

**Estado: NO PROBADO.** Este documento es un plan de ejecución manual. No hubo interacción real con Swing durante la Etapa 4.19; por lo tanto no se afirma apariencia, navegación ni mensajes visuales verificados.

Fecha: 2026-11-20 · Aplicación: `cu.pican.app.PicanApp` · Alcance: CU-01 a CU-19.

## 1. Precondiciones

1. Compilar: `.\scripts\compilar.ps1` (se espera `OK: 228 fuentes compiladas en out.`).
2. Definir credenciales solo por entorno o `-D`:
   - `PICAN_ADMIN_PASSWORD=<CLAVE_ADMIN_PRUEBA>`
   - `PICAN_DB_PASSWORD=<CLAVE_PG_PRUEBA>`
3. Para PostgreSQL usar una base temporal con `test` en el nombre y `pican.db.migrate=true`. Para MEMORY usar `PICAN_PERSISTENCE=memory`.
4. Roles disponibles: `ADMINISTRADOR`, `JEFE_DE_UEB`, `ESPECIALISTA_DE_PRODUCCION`, `ESPECIALISTA_DE_ESTADISTICA`. El directivo **no tiene cuenta**.

## 2. Casos propuestos

| ID | Rol / datos | Pasos | Resultado esperado |
|---|---|---|---|
| GUI-01 | Sin credencial administrativa configurada, PostgreSQL | Iniciar la aplicación sin `PICAN_ADMIN_PASSWORD` ni `-Dpican.admin.password`. | La aplicación no entra al menú; muestra error claro indicando que debe definirse la credencial. No aparece ninguna contraseña en pantalla ni en consola. |
| GUI-02 | ADMINISTRADOR, cuenta inicial con cambio obligatorio | Iniciar sesión con `admin` / `<CLAVE_ADMIN_PRUEBA>`. | El diálogo exige reemplazar la contraseña antes de mostrar módulos. Tras el cambio, entra al menú. |
| GUI-03 | ADMINISTRADOR | Revisar menú lateral. | Solo aparecen `Inicio` y `Administración` (CU-16/CU-17, con CU-19 como pestaña). No aparece `Producción`, `Estadística` ni `Sincronización`. |
| GUI-04 | ADMINISTRADOR | En Administración → Usuarios, intentar cambiar el rol del único administrador a otro rol. | La operación se rechaza con mensaje de última cuenta administrativa; el usuario conserva su rol. |
| GUI-05 | ADMINISTRADOR | Crear un segundo usuario ADMINISTRADOR y repetir GUI-04. | Ahora el cambio sí se permite, demostrando que la guarda no bloquea operaciones válidas. |
| GUI-06 | JEFE_DE_UEB | Iniciar sesión y revisar menú. | Accede a módulos de producción, vales, recepciones, incidencias y sincronización de UEB. No accede a Administración ni a informes de la central. |
| GUI-07 | ESPECIALISTA_DE_PRODUCCION | Iniciar sesión y revisar menú. | Accede a consultas de referencias históricas, metas e infraestructura (CU-01 a CU-03). No accede a administración ni sincronización. |
| GUI-08 | ESPECIALISTA_DE_ESTADISTICA | Iniciar sesión y revisar menú. | Accede a importación/validación de la central y generación de confirmaciones. No accede a Administración. |
| GUI-09 | JEFE_DE_UEB | En una confirmación con estado `RECHAZADO`, verificar la UEB de origen tras importarla. | La UEB no queda marcada como sincronizada y no aparece una nueva versión confirmada. |
| GUI-10 | JEFE_DE_UEB | Importar una exportación con versión menor que la local. | Se registra conflicto con motivo «Versión recibida anterior a la local» y no se sobrescribe el dato local. |
| GUI-11 | ESPECIALISTA_DE_ESTADISTICA | Generar confirmación de un paquete con conflictos de varios paquetes. | La confirmación muestra únicamente los conflictos del paquete seleccionado. |
| GUI-12 | ADMINISTRADOR | En CU-19, crear copia, ver manifiesto/auditoría y restaurar en una base temporal. | La restauración pide doble confirmación (diálogo + escritura literal `RESTAURAR`), registra auditoría y activa bloqueo local SIN-13 en `LOCAL`. |
| GUI-13 | ADMINISTRADOR, instalación `LOCAL` restaurada | Intentar emitir un vale y generar un paquete de exportación. | Ambas operaciones se rechazan con el bloqueo SIN-13. No se ofrece un desbloqueo inventado. |
| GUI-14 | ADMINISTRADOR | Revisar módulo `Inicio / Dashboard`. | Las tarjetas y tabla aparecen claramente como ficticias; no se presentan como indicadores reales. |
| GUI-15 | Directivo | Intentar iniciar sesión con un usuario directivo. | No existe cuenta; el acceso se rechaza. No se crea un rol nuevo. |

## 3. Datos de prueba sugeridos

- Usuario inicial: `admin`.
- Segundo administrador: `admin_segundo_gui`.
- Jefe de UEB: `jefe_gui`.
- Especialista de producción: `produccion_gui`.
- Especialista de estadística: `estadistica_gui`.
- UEB temporal: código `UGUI419`, nombre `UEB GUI 4.19`.
- Contraseñas: solo `<CLAVE_ADMIN_PRUEBA>` y `<CLAVE_APP_PRUEBA>`, nunca escritas en el código, logs o este documento como literales.

## 4. Registro de ejecución

| Caso | Fecha | Ejecutado por | Resultado observado | Evidencia |
|---|---|---|---|---|
| GUI-01 a GUI-15 | — | — | **NO EJECUTADO** | — |

La ausencia de este registro impide afirmar que la interfaz fue probada en la Etapa 4.19.
