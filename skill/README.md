# 🤖 Skill para agentes de IA: `preparar-tv`

Deja que **Claude Code** o **Codex** preparen tu TV por ti, igual que se hizo en los TV de prueba:

- 🧹 **Limpieza** de rastreo, telemetría y publicidad preinstalados (básica), y opcionalmente de apps basura del fabricante y de Google (profunda). Antes de quitar nada te muestra la lista y te pide confirmación. Todo se puede revertir.
- 🏠 Instala **Cristian Plaza TV** como pantalla de inicio.
- 🎮 Deja listos los **botones del control**.
- ✅ **Verifica** después de reiniciar, incluso si el fabricante reinstala apps a escondidas.
- ↩️ Genera un `restaurar.sh` para dejar el TV como estaba.

Está probada en Xiaomi y TCL con Google TV. En otras marcas usa un método más cauteloso y propone menos.

## Requisitos

- **Claude Code** o **Codex** en un computador (Mac o Linux recomendado) conectado a la misma red Wi-Fi que el TV.
- `adb` ([Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)). En Mac: `brew install --cask android-platform-tools`.
- En el TV: *Opciones de desarrollador* con **Depuración USB** y **Depuración por red** activadas (la skill te guía).

## Instalar la skill

**Claude Code**
```bash
git clone https://github.com/cristianplazat-cmyk/cristianplaza-tv.git
mkdir -p ~/.claude/skills && cp -r cristianplaza-tv/skill/preparar-tv ~/.claude/skills/
```

**Codex**
```bash
git clone https://github.com/cristianplazat-cmyk/cristianplaza-tv.git
mkdir -p ~/.codex/skills && cp -r cristianplaza-tv/skill/preparar-tv ~/.codex/skills/
```

**Cualquier otro agente:** pídele que lea `skill/preparar-tv/SKILL.md` y lo siga.

## Usarla

Abre una sesión nueva y escribe, por ejemplo:

> Prepara mi TV, su IP es 192.168.1.50. Quiero la limpieza profunda.

En Claude Code también puedes escribir `/preparar-tv`.

## ⚠️ Importante

- La skill **no requiere root** y no modifica el sistema: las apps se quitan solo para el usuario del TV y se restauran con `restaurar.sh` o con un restablecimiento de fábrica.
- Revisa la lista antes de aprobarla. Úsala bajo tu propia responsabilidad.
- ¿La probaste en otra marca? Comparte la lista que funcionó en un *issue* o *pull request* para agregarla a `listas.md`.
