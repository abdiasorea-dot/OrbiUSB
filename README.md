# OrbiUSB — etapa inicial USB sin ADB

Incluye una clase Android para detectar/abrir un accesorio AOA sólo mientras la
Activity está en primer plano, más el filtro de identificación y las instrucciones
de integración.

Esto todavía no duplica pantalla: abre el canal USB AOA. El receptor Linux debe
negociar AOA desde el dispositivo inicialmente conectado en MTP. Después faltan
el protocolo de vídeo, decoder Android y backend de pantalla virtual Linux.

No se modificó el repositorio remoto ni la rama `main`.
