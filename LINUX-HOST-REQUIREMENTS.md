# Receptor Linux: negociación automática

El usuario selecciona «Transferencia de archivos (MTP)» como modo inicial. MTP
sirve para transferir archivos, no es un canal USB genérico de vídeo. Por eso,
el receptor Linux debe detectar el dispositivo inicial y negociar Android Open
Accessory (AOA):

1. Detectar conexión USB y consultar si el dispositivo soporta AOA.
2. Consultar la versión del protocolo AOA.
3. Enviar las cadenas de identificación del accesorio: manufacturer `OrbiUSB`,
   model `OrbiUSB Display`, description, version `1.0`, URI y serial.
4. Solicitar el cambio a modo accessory y esperar la reenumeración USB.
5. Abrir los endpoints bulk del dispositivo ya enumerado como AOA.
6. Iniciar el protocolo OrbiUSB sobre ese canal.

Durante la sesión el Android normalmente deja de presentarse como MTP y pasa a
modo accessory. La selección MTP es el punto de partida para que el host detecte
y negocie el cambio, no el transporte de vídeo. No todos los Android soportan
AOA; compatibilidad depende del fabricante, firmware, puerto y cable.

Ya están: negociador Linux (OrbiUSB-PC-Starter 0.3.0), protocolo de mensajes e imagen de prueba. Faltan: streaming H.264,
decodificación MediaCodec, captura/encoder Linux y display virtual para KDE.
X11 y Wayland requieren backends distintos.

