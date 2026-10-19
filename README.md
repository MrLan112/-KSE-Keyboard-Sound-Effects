# [KSE] Keyboard Sound Effects

A client-side Fabric mod that plays a sound on every key press and mouse click.
Settings open with **B** (or from Mod Menu).

Current version: **1.0.1** · Author: **Mr_Lan123** · License: MIT

## Features

- Separate toggles for keyboard and mouse
- 8 built-in sounds (Creamy Keyboard, Rizz Sound Effect, Classic Click, Soft Click, Mechanical Keyboard, Soft Pop, ASMR Tap x2)
- Import your own sounds (WAV, MP3, OGG, AIFF, AU) with a custom name
- Preview button for every sound, volume slider
- Scrollable list (mouse wheel or scrollbar)
- "Play only when a specific key is pressed" with a key editor
- Silent until the player picks a sound
- Mod Menu support
- English and Russian

## Projects

One project covers several Minecraft versions. Pick the folder that matches your game.

| Folder | Minecraft | Java | Built with |
|---|---|---|---|
| `KSE-Mod-1.21.1-1.21.4` | 1.21.1 - 1.21.4 | 21 | Yarn |
| `KSE-Mod-1.21.5-1.21.8` | 1.21.5 - 1.21.8 | 21 | Yarn |
| `KSE-Mod-1.21.9-1.21.11` | 1.21.9 - 1.21.11 | 21 | Yarn |
| `KSE-Mod-26.1` | 26.1, 26.1.1, 26.1.2 | 25 | Mojang names |
| `KSE-Mod-26.2` | 26.2 | 25 | Mojang names |
| `KSE-Mod-26.3` | 26.3 | 25 | Mojang names |

## Building

```
cd KSE-Mod-1.21.1-1.21.4
./gradlew build        # Windows: gradlew.bat build
```

The mod jar is `build/libs/kse-keyboard-sound-effects-1.0.1.jar`
(ignore the `-sources.jar`). Use the JDK listed in the table.

## Built-in sounds

They live in `src/main/resources/assets/kse/sounds/` in every project.
To add a built-in sound, drop a `.wav` there, register it in
`src/client/java/com/mrlan123/kse/sound/SoundPlayer.java` and add a
`kse.sound.<id>` name to both files in `assets/kse/lang/`.

## Mod Menu

The `com.terraformersmc.modmenu.api` classes inside the projects are tiny
compile-time stubs, excluded from the jar. The real classes come from Mod Menu
at run time, and the mod works fine without it.

## Русский

Клиентский мод для Fabric: звук на каждое нажатие клавиши и кнопки мыши.
Настройки открываются клавишей **B** или через Mod Menu. Выберите папку проекта под вашу
версию Minecraft (таблица выше), соберите командой `./gradlew build`; готовый мод лежит в
`build/libs`. Встроенные звуки находятся в `src/main/resources/assets/kse/sounds/`.
Описание для Modrinth: `MODRINTH_DESCRIPTION.md` (English) и `MODRINTH_DESCRIPTION_RU.md`.
