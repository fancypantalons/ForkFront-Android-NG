# ForkFront-DS

ForkFront-DS is the Android UI library for [NetHack Android DS](https://github.com/fancypantalons/NetHack-Android-DS), a modernized Android port of NetHack 3.6.7. It implements the full game frontend: rendering, input handling, menus, dialogs, and settings — everything the player sees and touches.

It is consumed as a Git submodule by the parent project and compiled into the app via Gradle's composite build support.

## Origins

ForkFront-DS is a hard fork of [ForkFront-Android](https://github.com/gurrhack/ForkFront-Android) by gurrhack, originally conceived as a shared frontend for Android ports of NetHack variants. *Enormous* credit goes to [Martin Gurr](https://github.com/gurrhack) and the other project contributors for laying the groundwork that I've built on.

That said, this fork diverges substantially in architecture, input model, and design philosophy. There is no intention to merge changes back upstream, though obviously if anything I've done here is useful in that project, that would be fantastic!

## Design Philosophy

ForkFront-DS is built around a few core ideas inspired by my past work in [NetHackDS](https://github.com/fancypantalons/NetHack):

- **Touch and gamepad first.** The soft keyboard should never be required for gameplay — only for text
  entry. Navigation, commands, and menus should all work through touch gestures or a physical gamepad.
- **Native Android experience.** This is not a terminal emulator or a virtual keyboard strapped to a
  map view. Interactions should feel native: gestures, haptics, Android UI patterns.
- **Radical configurability.** NetHack is a deep game. The interface has to accommodate diverse
  playstyles rather than enforcing a single "correct" way to play.
- **Always fullscreen and immersive.** It's a game. There's no good reason to give up screen real estate.

## Architecture

ForkFront is a pure Java Android library (`com.tbd.forkfront`). It lives entirely in `lib/src/` and
communicates with the native NetHack engine via JNI.

### NH_State — The Brain

`NH_State` is the central coordinator. It owns the lifecycle, wires together the subsystems, and mediates
between the engine thread and the UI thread. When you're trying to understand how something fits together,
start here.

### Package Overview

| Package | Responsibility |
| :--- | :--- |
| `engine` | JNI bridge — `NetHackIO` receives calls from the native engine and dispatches them to the UI. `NH_Handler` defines the interface. |
| `window` | NetHack window implementations: map (`NHW_Map`), messages (`NHW_Message`), status bar (`NHW_Status`), menus (`NHW_Menu`). |
| `window.map` | Map rendering and input: tile drawing, viewport management, gesture controller, gamepad cursor. |
| `window.menu` | Menu and text window rendering. |
| `gamepad` | Full gamepad subsystem: context-aware key bindings, chord tracking, axis normalization, binding UI. |
| `input` | Touch input primitives: directional pad view, key event translation, touch repeat. |
| `widgets` | HUD overlay widgets: command palette, minimap, message area, status display. |
| `dialog` | Modal dialogs: character picker, text input, yes/no questions. |
| `commands` | Command picker UI for tap-to-send commands and pinned command shortcuts. |
| `context` | Contextual action engine — maps game state to available commands. |
| `settings` | Preferences UI and settings coordination. |
| `ui` | Core UI utilities: layout configuration, animation, theming, system UI control, secondary display. |
| `hearse` | Integration with the [Hearse](http://hearse.krangenstein.com/) bones file sharing service. |

### Gamepad Subsystem

The gamepad subsystem is context-aware: what a button does depends on where you are in the game
(`UiContext`). Bindings are stored per-context in a `KeyBindingMap` and persisted via `KeyBindingStore`.
Chord support (`Chord`, `ChordTracker`) allows modifier-style bindings (e.g., shoulder button + face button)
without consuming buttons as dedicated modifiers.

### Engine Communication

The native engine runs on its own thread. `NetHackIO` receives JNI calls from the engine and posts work
to the main thread via `NH_Handler`. Commands flow the other direction through `EngineCommandSender`.
The split keeps the UI thread responsive while the engine blocks waiting for player input.

## Building

ForkFront is not built standalone. See the [parent project](https://github.com/fancypantalons/NetHack-Android-DS)
for full build instructions. In short:

```bash
cd sys/android
./gradlew assembleDebug
```

The Gradle composite build picks up local changes to this submodule automatically.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
