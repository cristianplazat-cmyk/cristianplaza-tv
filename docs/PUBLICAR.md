# Cómo publicar una versión nueva (nota interna)

1. Sube `VERSION` y `CODIGO` en `build.sh` y anota los cambios en `CHANGELOG.md`.
2. `./build.sh` → genera `dist/CristianPlazaTV-<versión>.apk` firmado con `firma/`.
3. Arma el zip: APK + `scripts/*` → `CristianPlazaTV-<versión>.zip`.
4. `gh release create v<versión> dist/CristianPlazaTV-<versión>.zip dist/CristianPlazaTV-<versión>.apk --notes-file <notas>`

⚠️ Usa SIEMPRE la misma firma (`firma/`). Si se pierde, los usuarios tendrán que desinstalar para actualizar.
