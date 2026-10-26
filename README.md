# OrbiUSB
App funcional para establecer conexion de una tablet como pantalla duplicada o extendida en manjaro linux

# OrbiUSB Android — prototipo de interfaz 0.2.0

Proyecto nativo Android en Kotlin, con interfaz oscura y navegación entre **Conexión** y **Ajustes**.

## Incluye
- Pantalla principal «En espera de conexión».
- Resumen de preferencias de pantalla.
- Ajustes seleccionables de resolución, orientación y calidad.
- Guardado de preferencias en SharedPreferences para conservarlas al cerrar la app.
- Diseño adaptable con componentes nativos Android; sin dependencias de UI externas.

## Importante
Es un prototipo de interfaz. Los ajustes se pueden elegir y guardar, pero todavía no afectan a una transmisión real: falta desarrollar la comunicación USB con el host Linux y el motor de vídeo.

## Abrir y ejecutar
1. Abre la carpeta `AndroidOscuraStarter` en Android Studio.
2. Deja que Gradle sincronice el proyecto.
3. Conecta una tablet Android o inicia un emulador.
4. Pulsa **Run** para instalar la aplicación.

Requisitos: Android Studio y SDK para API 35; mínimo Android 9 (API 28).
