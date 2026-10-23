# Espacio Infinito

App Android (Java, sin dependencias AndroidX) que guarda tus fotos, vídeos, APKs y descargas en tus **Mensajes guardados de Telegram** (almacenamiento ilimitado gratis) y libera espacio en el móvil. Usa TDLib (`io.github.tdlib-android:core:0.1.1`).

- Archivos > 1950 MiB (3900 MiB con Premium) se trocean solos; el pie de cada mensaje guarda nombre, ruta, parte y SHA-256.
- Nunca borra un archivo local hasta que el servidor confirma todas las partes y los tamaños coinciden.
- Galería con miniaturas locales, streaming de vídeo (servidor local 127.0.0.1), descargar/compartir/exportar, modo automático por umbral y gráfico de espacio.

Compilar: `./fetch-tdlib.sh && ./gradlew assembleRelease` (JDK 17, Android SDK 34).
