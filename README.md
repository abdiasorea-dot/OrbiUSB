# OrbiUSB — etapa inicial USB sin ADB

Incluye una clase Android para detectar/abrir un accesorio AOA sólo mientras la
Activity está en primer plano, más el filtro de identificación y las instrucciones
de integración.

Esto todavía no duplica pantalla: abre el canal USB AOA. El receptor Linux debe
negociar AOA desde el dispositivo inicialmente conectado en MTP. Después faltan
el protocolo de vídeo, decoder Android y backend de pantalla virtual Linux.

No se modificó el repositorio remoto ni la rama `main`.

# OrbiUSB — app Android (0.2.0) (08/11/26)

Receptor Android de OrbiUSB por USB sin ADB (Android Open Accessory).

- `UsbAccessorySession`: detecta y abre el accesorio AOA mientras la app está en primer plano (sondea cada 1,5 s porque Android no avisa a una app abierta).
- `OrbiLink`: protocolo con el PC (HELLO/HELLO_ACK, PING/PONG, IMAGE/IMAGE_ACK) en un hilo propio.
- `MainActivity`: muestra el estado de la conexión y la última imagen recibida.

Todavía no hay vídeo: el PC puede enviar una imagen de prueba. Ver `LINUX-HOST-REQUIREMENTS.md`.
