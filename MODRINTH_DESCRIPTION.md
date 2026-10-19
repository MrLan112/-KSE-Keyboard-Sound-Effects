# [KSE] Keyboard Sound Effects

Minecraft is a pretty quiet game when you type. I wanted my keyboard to *feel* like a keyboard, so I made this: every key press and every mouse click plays a short sound of your choice. That's the whole idea, and it's all done from one small settings screen in the game.

It's client-side only, so you install it on your own game and nothing is needed on the server.

## What you get

- **Sounds for keys and clicks.** Turn the keyboard part and the mouse part on or off separately.
- **8 built-in sounds.** Creamy Keyboard, Rizz Sound Effect, Classic Click, Soft Click, Mechanical Keyboard, Soft Pop and two ASMR taps (gentle and deep).
- **Your own sounds.** Hit "Add custom sound", pick a file, give it a name and it shows up in the list. WAV, MP3, OGG, AIFF and AU files work. You can delete your own sounds from the same list.
- **Listen before you pick.** Every sound has a little play button right next to "Select", so you can try them all without switching.
- **Volume slider.** Right in the settings, nothing to edit by hand.
- **A proper scrolling list.** Use the mouse wheel or drag the scrollbar, same as on a website. Add as many sounds as you want.
- **"Play only when a specific key is pressed".** Switch this on and the Key editor button unlocks. Click "Add key", press whatever key or mouse button you want, then confirm. You can add as many as you like and remove them any time. Handy if you only want sound on WASD, or only on the mouse buttons.
- **Quiet by default.** After a fresh install nothing makes noise until you choose a sound yourself.

## How to open the settings

Press **B** in game (you can change the key in Options > Controls, it's under "[KSE] Keyboard Sound Effects"). If you have [Mod Menu](https://modrinth.com/mod/modmenu) installed, you can also open the settings from there with the config button on the mod's page.

## Supported versions

Fabric, Minecraft **1.21.1 to 1.21.11**, and **26.1, 26.2 and 26.3** (26.1.x too).

You need:
- Fabric Loader
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21 for 1.21.x, Java 25 for 26.x (the game already uses the right one if you launch it the normal way)
- Mod Menu is optional

## Good to know

- It listens to raw key and mouse presses, so it also plays in menus, in chat and everywhere else, not only in the world.
- Sounds are played on a separate thread, so they shouldn't cost you any FPS.
- Settings live in `config/kse/config.json` and your imported sounds are copied to `config/kse/sounds/`. Delete the folder if you ever want a clean start.
- The mod uses Java's own audio system and doesn't touch Minecraft's sound settings, so the in-game master volume doesn't change it. Use the slider in the KSE menu.
- Available in English and Russian.

## Something not working?

If it breaks on your version, open an issue on [GitHub](https://github.com/MrLan112/-KSE-Keyboard-Sound-Effects/issues) or [Discord](https://discord.gg/7qzAw5Cckx) and attach your `latest.log` from the `logs` folder. That makes it way easier to fix.

Made by Mr_Lan123. MIT license.
