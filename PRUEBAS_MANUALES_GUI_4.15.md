# PRUEBAS MANUALES GUI — ETAPA 4.15 (PENDIENTES DE EJECUCIÓN POR EL USUARIO)

Estado: **no ejecutadas**. Esta lista se prepara para que el usuario las realice después; ninguna pantalla se ha probado manualmente durante la automatización.

Requisito previo: ejecutar `scripts\compilar.ps1` y `scripts\ejecutar.ps1 -Modo postgresql` con una base exclusiva de pruebas; iniciar sesión con una cuenta activa.

| # | Pantalla / módulo | Pasos sugeridos | Resultado esperado |
|---|---|---|---|
| 1 | Acceso | Iniciar sesión con usuario y contraseña válidos; intentar contraseña incorrecta. | Acceso correcto; la contraseña errónea se rechaza sin abrir el menú. |
| 2 | Inicio / Dashboard | Abrir el módulo y comprobar el encabezado con usuario y rol. | El rol mostrado coincide con la cuenta autenticada. |
| 3 | Producción de huevos | Registrar un parte con valores válidos y consultarlo por fecha. | El parte aparece en la consulta; los valores numéricos se conservan. |
| 4 | Producción de carne | Registrar producción y consultar el período. | Cantidad decimal visible con su precisión. |
| 5 | Consolidación | Consolidar un período y abrir el detalle. | Estado y motivo parcial visibles cuando aplique. |
| 6 | Entregas y vales | Emitir un vale; anular otro confirmando que el producto no salió. | Número de vale asignado; el vale anulado conserva motivo y fecha. |
| 7 | Recepción de entregas | Registrar una recepción conforme y otra no conforme. | El vale conforme pasa a CERRADO; el no conforme queda con incidencia. |
| 8 | Incidencias | Completar y cerrar una incidencia. | UUID, observaciones y fecha de compromiso se conservan. |
| 9 | Análisis e informes | Consultar indicadores, metas y producción; elaborar informe, registrar fuente, decisión y entrega. | Informe cambia de estado solo en el orden permitido. |
| 10 | Administración | Alta de usuario, asignación de rol y alta de catálogo. | Cambios visibles al recargar la pantalla. |
| 11 | Sincronización (al final de la etapa) | Generar paquete, elegir ubicación USB, validar/importar paquete de prueba, consultar historial y conflictos. | Mensajes comprensibles; operaciones solo para los roles CU-08/CU-09. |
| 12 | Cierre de sesión | Cerrar sesión desde el encabezado. | Vuelve a la pantalla de acceso; no quedan operaciones habilitadas. |

Notas:

- No se debe usar la base productiva ni el servicio PostgreSQL de trabajo.
- Los resultados de esta tabla se registrarán aquí cuando el usuario los ejecute; mientras tanto permanecen como pendientes.
- La automatización de la Etapa 4.15 cubre la lógica y la integración, no la apariencia ni la interacción manual.
