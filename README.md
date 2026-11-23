# Face (app social liviana)

Visor Android ultraliviano (Java puro, sin dependencias, APK de unos 14 KB) de la versión web móvil de Facebook
(`m.facebook.com`), pensado para celulares con poca memoria. No es una app oficial de Facebook ni está afiliada a Meta:
es un WebView que abre el sitio público.

## Funciones

- Abre `m.facebook.com` con sesión guardada por las cookies del WebView.
- Mensajes: Facebook bloquea los chats en navegadores de celular, así que se abren dentro de la app con la versión
  de computadora (`www.facebook.com/messages`), y al salir se vuelve a la versión de celular. Desde el menú se puede
  elegir abrirlos en Messenger.
- Página propia de "Sin conexión" con botón Reintentar.
- Oculta los carteles de "Abrir la app / Descargar la app".
- Recibe fotos y videos compartidos desde la galería para publicarlos, y enlaces para compartir con el diálogo
  oficial `sharer.php`.
- El botón "Compartir" abre el menú de Android (WhatsApp, etc.).

## Compilar

Requisitos: JDK 17 y Android SDK (minSdk 24, targetSdk 35).

```bash
bash tools/compilar.sh            # debug  -> app/build/outputs/apk/debug/app-debug.apk (usa --offline)
bash tools/compilar.sh release
```

`tools/env.sh` toma el JDK y el SDK de la carpeta indicada en la variable `HERRAMIENTAS` (con `jdk/` y `android-sdk/`).
`local.properties` no se sube: lo crea Android Studio o se escribe a mano con `sdk.dir=<ruta al Android SDK>`.
