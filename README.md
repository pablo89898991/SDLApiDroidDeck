# SDLApiDroidDeck

**DroidDeck with SDL3 built in.** One app, **SDL3DroidDeck**, that runs Steam on Android like DroidDeck does, plus
full support for any controller SDL3 knows, and Steam Native Hook-up. Plug a controller in with USB-C, and Steam
sees the **real controller**, exactly as if it were plugged into a PC.

> Unofficial fork of [DroidDeck](https://github.com/Droid-Deck/droiddeck). It is not made by or affiliated with
> the DroidDeck project. It tracks upstream DroidDeck automatically and adds the features below on top.

> One APK: the SDL3Deck fork (DroidDeck plus a patch) and the SDL3Bridge app combined. It keeps DroidDeck's app ID
> (`com.droiddeck.launcher`), so it installs over SDL3Deck or DroidDeck **with your games and settings kept**.

---

## Highlights

- **Steam Native Hook-up**: Steam gets your actual USB controller (DualSense, DualSense Edge, Xbox, Switch Pro,
  Flydigi, 8BitDo, Steam Controller…) and runs its own driver for it, with every button, paddle, touchpad,
  gyro, light and haptic motor.
- **Any SDL3-supported controller**, up to **4 at once**. USB gives full features; Bluetooth works through
  Android's input.
- **Present a controller as** a Steam Deck pad, DualSense, DualSense Edge, DualShock 4, Steam Controller 2026
  or Steam Controller 2015.
- **Steam Controller (2026)** over a USB cable or its wireless puck, driven natively by Steam without Bluetooth
  latency.
- **Low-latency path**: controller reports go straight into shared memory and wake Steam immediately, and input
  never piles up into lag.
- **Everything is set inside DroidDeck**: a settings tab, plus the same page in the in-game drawer, so you can
  change things while you play.
- **Runs silently in the background** with a status bar icon. Nothing pops up over your game.
- **Per-player settings** (P1–P4): gyro, touchpad, button remap, lightbar.
- **Floating bubble**: tap to switch the SDL3 driver on or off, double tap for Steam Native Hook-up.
- **Works on the desktop too**: every mode (present-as types and Hook-up) also works with Steam on DroidDeck's
  desktop, set up like a PC.
- **Windows components for desktop programs**: right-click any Windows program on the desktop -> Windows
  Components... to add Visual C++ runtimes, DirectX extras, .NET, XNA and more to its prefix.
- **Auto-sync**: rebuilt on the newest upstream DroidDeck every day.

---

## Steam Native Hook-up

The controller is handed to Steam as itself instead of being re-presented as another pad.

- SDL3 finds which USB interface is the game controller (anything SDL3's USB driver supports), lets go of it and
  opens it raw.
- The controller's real identity is passed on: vendor/product ID, release, name, serial, interface, and the HID
  report descriptor read from the device itself. Every HID report travels both ways.
- Inside the Steam environment it appears as `/dev/hidraw24`–`27` with a kernel-like sysfs description, so
  Steam's own driver for that controller runs, as on a PC. Examples:
  - a Flydigi Vader 4 Pro with its 4 back buttons, C/Z and gyro
  - a DualSense / DualSense Edge with touchpad, mute, paddles and Fn
  - a Switch Pro or Xbox controller
- Steam handles calibration, lights, haptics and gyro itself, and you configure the controller in Steam, like on
  a PC.
- **Game rumble goes to Steam**: a game's rumble on Steam's virtual pad is passed to Steam through force
  feedback, so Steam rumbles the real controller with its own driver.
- **No extra pad**: while controllers are handed to Steam, DroidDeck's own Steam Deck pad steps aside, so the
  real controller is player 1.
- **Up to 4 controllers**, with hot-plug.
- **Steam Controller (2026)** is handed over too, by cable (`28de:1302`) or puck (`28de:1304`/`1305`). Only
  the interface that actually sends controller data is used; the boot keyboard and mouse interfaces (lizard
  mode) stay with Android.
- **Switching**: double-tap the floating bubble, or pick it in *Present controller as*. Switching it off returns
  to your previous mode and SDL3 driver state.

**Steam always reads the newest state** (see [Low latency for every controller](#low-latency-for-every-controller)).

**Fast path.** Reports from the controller are written straight into the shared hook-up memory by a
high-priority thread, about 250 µs after the USB layer receives them, and Steam's reader is woken with a futex.
There is no Java hop and no message per report. Steam's output reports (rumble, lights, settings) and feature
reads go back the same way, handled by their own writer thread. In a test harness, report-to-Steam time went
from a median of 1.45 ms to 0.29 ms.

---

## Any SDL3-supported controller

- **Auto mode** (default):
  - **Over USB**, SDL3's own driver reads the controller with all its extras: gyro, touchpads, back paddles,
    misc buttons. That covers PlayStation, Xbox, Switch / Switch 2, Steam Controller 2015, 8BitDo, Flydigi,
    Hori, PowerA and the rest of SDL3's list.
  - **Over Bluetooth**, or for anything SDL3's USB driver can't open, the controller is read through Android's
    input.
  - A controller that SDL3 already drives over USB is never doubled by its Android copy.
- **Forced mode**: USB only, SDL3's driver only.
- **Unmapped generic controllers** (arcade sticks, cheap USB pads) get a sensible default layout.
- **SDL3 driver switch**: off hands the controller back to Android and DroidDeck. When Hook-up is switched off,
  the switch returns to how it was before.

## Present controller as

How Steam sees the controller when it isn't handed over raw:

| Mode | What Steam sees |
|---|---|
| Steam Deck (default) | DroidDeck's Steam Deck pad |
| DualSense | A real-looking Sony DualSense (touchpad, gyro) |
| DualSense Edge | A separate Edge pad type with native back buttons and Fn |
| DualShock 4 | A Sony DualShock 4 |
| Steam Controller 2026 | `28de:1302` |
| Steam Controller 2015 | `28de:1102`. The touchpad is split into the two trackpads, the D-pad clicks the left pad's edges, and back buttons / Fn go to the grips |
| Steam Native Hook-up | The real controller (see above) |

Sony rumble is decoded the way Steam and SDL actually send it (enhanced rumble, and stops are honoured), and
rumble goes to the controller instead of the phone.

## On DroidDeck's desktop

Every mode works with Steam on the desktop (and with Steam's desktop UI), not only in the Steam session. The
desktop is set up the way a PC is, with Steam as one program among others:

| Who | What it sees |
|---|---|
| Steam (desktop client, arm64 or x86-64) | The controller as you present it, or the real controller with Hook-up |
| Games Steam starts | Steam's virtual pads (Steam Input), as in the Steam session |
| Other desktop programs | Xbox 360 controllers, as in upstream DroidDeck. They don't see the nodes meant for Steam, so no controller shows up twice |

**Hook-up waits for Steam.** On the desktop, Steam may not be running. Until it starts (and 5 s after it quits),
the controllers stay ordinary gamepads that every desktop program can use. Once the Steam client runs, they're
handed to it within about 1.5 s, hot-plugged as on a PC. While Steam runs, other programs get the controllers
through Steam: add them to Steam as non-Steam games, or use Steam's Desktop Configuration.

Like on a PC, Steam applies its **Desktop Layout** (mouse on a stick or trackpad, keys on buttons) to the controllers
it drives while no game runs. Desktop programs also see a presented controller as an Xbox 360 pad, so both act at
once. Turn the Desktop Layout off or change it in Steam's controller settings if you don't want that.

### Windows components for desktop programs

Windows programs on the desktop run with Proton (DroidDeck's Wine), each in its own prefix. To give one the runtimes
it needs:

1. In the file manager, right-click the `.exe` or `.msi` -> **Windows Components...**
2. DroidDeck's Windows components window opens over the desktop for that program's prefix: the same catalog,
   recommendations and downloads as a game's settings (Visual C++ runtimes, DirectX extras, .NET, XNA, fonts...).
3. Pick what it needs. The components are added the next time the program starts, so close it first if it's
   running.

The picks belong to the prefix, so a Steam game's are the same as in its game settings. Use touch in the window:
the bridged controller keeps driving the desktop meanwhile.

## Steam Controller (2026) over cable or puck

Without Hook-up, every HID report of a cabled or puck-connected Steam Controller (2026) is passed through
unchanged to DroidDeck's own Steam Controller node, the same one its Bluetooth support uses. Steam drives it
natively: capacitive stick touch, grip sense, trackpad pressure, gyro, haptics and settings. Everything Steam
sends back, including feature reads, goes to the controller.

## Multiple controllers

- Up to 4 controllers, each in its own player slot.
- Player 1 has every feature.
- Players 2–4 are full gamepads with gyro, rumble and their own lightbar colour.
- When player 1 is unplugged, the next controller moves up.

## Per-player settings (P1–P4)

| Group | Settings |
|---|---|
| Gyro | Speed (5–800 %), invert axes, per-axis remap (DroidDeck X/Y/Z from any controller axis), accelerometer follows the remap, deadzone, one-tap calibration that removes drift, reset |
| Touchpad | Split (left half = left trackpad, right half = right trackpad), Single (whole pad = right trackpad) or Off; invert X, invert Y, swap X/Y |
| Button remap | L4, R4, L5, R5 and left/right trackpad click and touch, each from *any* controller button, including extra ones like a Flydigi's C/Z (MISC2–6) |
| Lightbar | Red, green, blue, brightness, player LEDs |

Global settings: present-as mode, SDL3 driver, controller source, poll rate (250 / 500 / 1000 Hz) and the
floating bubble.

## Settings inside DroidDeck

- **SDL3Bridge tab** in DroidDeck's Settings, with every setting above.
- **The same page in the in-game drawer**, with controller navigation, so you can change things mid-game.
- **Live status**:
  - players with name, ID and battery
  - controllers handed to Steam, and whether the fast path is on
  - the Steam Controller passthrough
  - how SDL3 runs
- Long option names stay readable: the value chip shows the short name and the menu shows the details.

## Runs in the background

- SDL3 runs in a background service with no screen. Starting a session or plugging in a controller starts it
  silently, and nothing comes to the front.
- **Status bar icon** showing the bridge's current status. Tapping it opens the controller test screen.
- **Self-checking start**: if SDL3 can't start in the background on a phone, the app says so and falls back to
  starting it when you open the controller screen. Nothing pops up by itself. The reason, and the crash report
  if there is one, go to the session log.

## Floating bubble

A draggable chat-head over any app:

- **Tap**: SDL3 driver on (green) or off (red).
- **Double tap**: Steam Native Hook-up on/off (blue, "HOOK").
- **Hold 3 s**: opens the controller screen.
- **Drag to the bottom**: dismiss.
- The position is remembered.

## Controller test screen

- A live diagram that draws only what the connected controller really has: buttons, sticks, triggers, one or two
  touchpads, paddles.
- Labels match the controller type: PlayStation ✕●■▲, Xbox A/B/X/Y, Nintendo, Steam, or generic.
- Colours: orange = held now, green = worked at least once, grey = not tested yet.
- Buttons for rumble and lightbar tests, and a reset.

## USB audio

Shows the USB or headset audio outputs Android sees (for example the controller's own audio over USB-C), with
a button for Android's media-output picker to switch between the controller and the phone.

## Low latency for every controller

Every controller node Steam reads, hooked-up or presented by DroidDeck (Steam Deck pad, DualSense / Edge,
DualShock 4, Steam Controller 2015 / 2026), is answered from a mailbox at the moment Steam reads it, instead of a
small queue whose newest reports were dropped when Steam read late:

- **Steam takes everything waiting** (like SDL's update loop): it gets every report in order, the newest last, so
  the current state arrives in that same read and gyro / touchpad keep all their samples.
- **Steam takes one report at a time while it is busy**: it gets the newest one.
- Which way Steam reads is learned while it runs. No press or release is ever skipped, however short: which bits
  are buttons is learned from the reports themselves (and the controller's HID descriptor), so no per-controller
  layout is needed.
- Steam's controller threads (reading controllers, writing its virtual pad) run at a higher priority, like
  Android's own input threads.

In a simulation where Steam comes back only every 15-45 ms, a 15 ms tap on DroidDeck's Steam Deck pad reached
Steam after 18-20 ms on average instead of 26 ms (13 vs 37 ms at 1000 Hz, 19 vs 159 ms when Steam reads one
report at a time), with no taps lost instead of up to 4 in 10. When Steam reads in time nothing changes (~2 ms).

## Performance extras

- **No lag build-up**:
  - frames are sent only when the controller changed
  - DroidDeck keeps the latest state per controller instead of a growing queue, so every button change is kept
    and stale stick/gyro frames are dropped
  - Steam reads the newest state of every controller (above)
- **Steam's virtual pad paced for games**: stick and trigger movement reaches the game at most 250 times a second
  (newest values), buttons instantly. Steam's 1000 updates a second made Windows games lag under emulation; Steam
  itself still reads the controller at full rate, so gyro-to-mouse keeps it.
- **Poll rate** up to 1000 Hz. In Steam Native Hook-up it also paces how often Steam gets the newest report.
- **Game thread boost** (Android performance hints, ADPF): unlocked for any Android 12+ device, with a switch
  in Settings → Performance and in the drawer's Latency group.
- **Steam started with `+controller_rate 500`**.
- **Steam Input mouse clicks reach games**: upstream's Remote Play touch shim dropped every mouse click Steam
  sends (it couldn't find the real XTest function, which Steam loads privately). Fixed for every controller and
  mode; keys and mouse movement were never affected. Upstream now has the same fix, so it's kept as theirs.
- **Controller-driven mouse works out of the box**: "Draw the cursor inside gamescope" (Performance -> Session
  fixes) starts on, so buttons bound to mouse clicks in Steam Input work. Turn it off for a little more speed if
  you never use controller mouse bindings.

## Diagnostics (session log, `pad.log`)

| Line | Meaning |
|---|---|
| `latency: button press waited for Steam to read it` | From a press reaching DroidDeck until Steam read it, any controller (avg / max every 10 s) |
| `latency: Steam read a button press -> on Steam's virtual pad` | Steam's own processing of a press (gamepad mappings) |
| `latency: Steam's virtual pad -> game read` | How long the game took to pick it up |
| `hookup: … / deck: … reports/s, Steam read …` | Steam was slow to read a controller: how many reports it read, how late, and which way it reads |
| `hookup: … the controller fell behind` | Steam sent output reports faster than the controller took them |
| `thread N (…) now runs at higher priority` | One of Steam's controller threads was raised |
| `hookup: Steam runs on the desktop` / `Steam is not running on the desktop` | On DroidDeck's desktop: whether Hook-up hands controllers to Steam right now |
| `mouse: …` | Steam Input's mouse clicks / keys on their way to the game: handed over by the X server, received by gamescope |
| `bridge: input lag, last 10 s` | Bridge frames arrived late in DroidDeck |
| `sdl3bridge: …` | The bridge's own log: hand-overs, fast path, engine start-up |

---

## Requirements and limits

- Android 12+ with USB host (USB-C OTG) for full features.
- **Bluetooth controllers can't be handed to Steam raw**: Android doesn't give apps raw HID access to them.
  They keep working as a DroidDeck pad.
- **In Hook-up mode** the per-player settings (remaps, gyro tuning, lightbar) don't apply, because Steam owns
  the controller. Configure it in Steam.
- **When the phone is fully loaded, Steam itself can still read controllers late.** Input now always reaches it as
  the newest state, and the latency lines above show where any remaining time goes.

## Installing

1. Download `SDL3DroidDeck.apk` from the newest release on the **Releases** page (every successful build is published
   there by itself).
2. Install it over SDL3Deck (or DroidDeck): same app, your games, prefixes and settings stay.
3. **Uninstall the separate SDL3Bridge app** (SDL3BridgeDroidDeck). It is built in now, and two copies would fight
   over the same USB controller.
4. The bridge starts with default settings, because Android keeps each app's settings apart. Set them again in
   **Settings → SDL3Bridge** (or the in-game drawer's SDL3Bridge page). Android asks once for USB access to the
   controller, and once for "display over other apps" if you want the floating bubble.

## How it is built

- **Thin fork**: every change is applied by a patch script (`patch-droiddeck.sh`) on top of the newest upstream
  DroidDeck. The script uses exact anchors and leaves a feature out with a warning rather than breaking the
  build.
- **Daily auto-sync**: a scheduled workflow overlays the latest upstream DroidDeck, re-applies the patch, commits
  and builds the APK. It overwrites instead of merging, so there are never git conflicts. Upstream-only
  workflows are skipped.
- **One source for the bridge**: the sync takes the bridge's code and the patch script straight from the
  [SdlBridge](https://github.com/pablo89898991/SdlBridge) repo, so a change pushed there is in the next build.
  Run *Sync upstream + SDL3 bridge* by hand to build it right away.
- **SDL3** comes from its pinned release (`droiddeck/sdl3.env` in SdlBridge, SHA-256 checked) and is built into
  the APK. The bridge runs in its own process inside the app (`:sdl3`), so a controller-driver crash can't take your
  Steam session down.
- **Signing**: built by upstream's own workflow and signed with its test key, like SDL3Deck, so each build installs
  over the last one.

### Setting the repo up (once)

- `main` holds this README, `LICENSE`, `.github/workflows/sync.yml` (the daily sync), `.github/workflows/build.yml`
  (only so *Build APK* can be started on the build branch) and `.github/workflows/publish.yml` (each successful build
  becomes a release with `SDL3DroidDeck.apk`; the newest 10 are kept). The APK is built on `sdl3droiddeck`, which the
  sync fills.
- Repo secret `SYNC_PAT`: a GitHub token with **repo** + **workflow** scope (GitHub doesn't let the built-in token
  push workflow files). Optional: `DOCKERHUB_USER` / `DOCKERHUB_TOKEN`, so the build pulls its Debian image from
  Docker Hub signed in.
- Then *Actions → Sync upstream + SDL3 bridge → Run workflow*. It runs daily after that.

## License and credits

- **GPLv3**, like upstream DroidDeck. The full source is in this repo.
- [DroidDeck](https://github.com/Droid-Deck/droiddeck): the original project, by its authors.
- [SDL3](https://github.com/libsdl-org/SDL) (zlib license): the controller drivers.
- Steam and the Steam Deck are trademarks of Valve Corporation. This project is not affiliated with Valve.
