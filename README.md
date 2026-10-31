<h1 align="center">Inlay: a keyboard for your game controller on Android</h1>

<p align="center">
  <picture>
    <source media="(prefers-reduced-motion: reduce)" srcset="docs/images/hero-locked.png">
    <img src="docs/images/hero.gif" alt="Inlay, an Android keyboard you type on with a game controller: the words 'Type with your controller' typed with the D-pad and A button, the right stick taking the suggested word, above a controller, a handheld, a vertical handheld and a TV remote" width="100%">
  </picture>
</p>

<p align="center">
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/dakingeman/inlay?style=flat-square&color=2A2C35&labelColor=16171D&label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-2A2C35?style=flat-square&labelColor=16171D" alt="Android 10 or newer">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/dakingeman/inlay?style=flat-square&color=2A2C35&labelColor=16171D" alt="Licence: GPL-3.0"></a>
  <img src="https://img.shields.io/badge/internet%20permission-none-2A2C35?style=flat-square&labelColor=16171D" alt="No internet permission">
</p>

<p align="center">
  <a href="../../releases/latest"><img src="https://img.shields.io/badge/Download%20the%20APK-72D6E8?style=flat-square&logo=android&logoColor=0B0B0E&labelColor=72D6E8" alt="Download the APK" height="34"></a>
  &nbsp;
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/dakingeman/inlay"><img src="https://img.shields.io/badge/Get%20it%20with%20Obtainium-2A2C35?style=flat-square" alt="Get it with Obtainium" height="34"></a>
</p>

<p align="center">
  <a href="#install">Install</a> · <a href="#features">Features</a> · <a href="#controls">Controls</a> · <a href="#questions">Questions</a> · <a href="#tested-on">Tested on</a> · <a href="CHANGELOG.md">Changelog</a> · <a href="../../issues/new/choose">Report a problem</a>
</p>

<br>

**Inlay is a free, open-source keyboard for Android that you type on with a game controller.** Move with the D-pad or stick, press A to type, and never reach for the touchscreen. It's made for Android gaming handhelds like the AYN Odin, Retroid Pocket, Anbernic and AYANEO, works on any Android phone or tablet with a gamepad connected, and on Android TV and Google TV with just the remote.

It's a real system keyboard, so it works wherever you type: searching your game library, logging in, Wi-Fi passwords, chats and web addresses. Word suggestions, accents, copy and paste and undo are all on the controller.

Free and open source. No ads, no tracking, no internet permission.

https://github.com/user-attachments/assets/723c8fae-523f-497d-ab7a-cccda66b2d7c

<p align="center"><b>The one-minute teaser</b> (turn the sound on) · <a href="../../releases/tag/v1.2.0">What's new in 1.2.0</a></p>
<p align="center"><sub>The keyboard, the screens and the devices in these pictures and videos are drawn by the app's own code, and every sound is the app's own.</sub></p>

<br>

## What it looks like

<p align="center"><img src="docs/images/pack/typing.png" alt="Move with the D-pad, press A" width="100%"></p>

<p align="center"><img src="docs/images/pack/words.png" alt="Words show up as you type" width="100%"></p>

<p align="center"><img src="docs/images/pack/accents.png" alt="Hold A for accents" width="100%"></p>

<p align="center"><img src="docs/images/pack/devices.png" alt="Made for what you hold" width="100%"></p>

<details>
<summary><b>See all 14 screens</b></summary>
<br>

<p align="center"><img src="docs/images/pack/hold.png" alt="Hold RT for more" width="100%"></p>

<p align="center"><img src="docs/images/pack/editing.png" alt="Select, copy and paste" width="100%"></p>

<p align="center"><img src="docs/images/pack/tidy.png" alt="Tidies up as you type" width="100%"></p>

<p align="center"><img src="docs/images/pack/looks.png" alt="Make it look how you want" width="100%"></p>

<p align="center"><img src="docs/images/pack/icons.png" alt="Icons for your controller" width="100%"></p>

<p align="center"><img src="docs/images/pack/tv-mode.png" alt="TV mode" width="100%"></p>

<p align="center"><img src="docs/images/pack/languages.png" alt="Eleven languages" width="100%"></p>

<p align="center"><img src="docs/images/pack/sound-packs.png" alt="Nine sound packs" width="100%"></p>

<p align="center"><img src="docs/images/pack/tour.png" alt="A one-minute tour" width="100%"></p>

<p align="center"><img src="docs/images/pack/settings.png" alt="Settings, made for the controller" width="100%"></p>

</details>

## Install

1. Download the latest `.apk` file from the [Releases](../../releases/latest) page onto your device.
2. Open it and allow installing from this source when Android asks.
3. Open **Inlay** and choose **About → Turn on Inlay**.
4. Choose **About → Switch keyboard** to make it the active keyboard.

A one-minute tour opens the first time, and you can replay it any time from **About**.

**Updates:** the free [Obtainium](https://github.com/ImranR98/Obtainium) app can watch this page and install new versions for you. In Inlay, choose **About → Get updates automatically**, or tap the Obtainium button at the top of this page on your device.

**Android TV or Google TV:** install the same `.apk` (for example with a file manager or "Send files to TV"), open **Inlay** from the apps row and follow the set-up card. TV mode turns on by itself there.

**Requirements:** Android 10 or newer, and a built-in controller or a connected gamepad.

## Features

| Feature | What it does |
|-|-|
| **Made for controllers** | D-pad or left stick moves the highlight the instant you press, with smart repeat when you hold a direction. A light stick push moves gently, a full push fast. |
| **Every button has a job** | A types, X deletes (hold to speed up), Y is space, Start is enter. LB and RB move the cursor, RT is shift, LT is symbols, and the right stick moves the cursor too. See [Controls](#controls). |
| **Word suggestions that learn** | Words you probably mean show in the top row as you type. Press the right stick in for the first, or move up and pick one. It learns the names and titles you use, and only keeps a word once you've really typed it. |
| **Hold A for accents** | Hold A on a letter and its accents rise into the top row: move to one and let go. |
| **Hold RT for more** | Small tips appear on the keys: a capital letter, word jumps, accents and undo. Nothing to memorise. |
| **Tidies up as you type** | A capital starts each sentence, a lone i becomes I, and a space typed before a full stop or comma moves after it. Each has its own switch. |
| **Edit text properly** | Select, cut, copy, paste and undo from the controller. Press Select twice to select everything. The right stick moves the cursor or grows a selection, faster the further you push (turn it off in Settings → Typing). |
| **Clipboard history** | Your last six copies, one button away. Anything marked as sensitive (passwords) is never kept. |
| **11 languages** | English, German, French (AZERTY), Spanish, Portuguese, Norwegian, Danish, Swedish/Finnish, Turkish, Russian and Ukrainian. Pick up to three and switch with RT + LT. Hold A (or press RT + Y) for accents (é, ñ, ł and more), so Italian, Dutch, Polish and others work too. |
| **Smart keys** | / and .com in web addresses, @ in email boxes, a big number pad for numbers. |
| **TV mode** | A compact floating keyboard with a big line showing what you type, readable from the sofa. Turns on by itself when you dock. |
| **Made for what you hold** | A controller, a handheld, a vertical handheld (with or without sticks) or a TV remote. Automatic recognises handhelds like the AYN Odin and Retroid Pocket, or pick one yourself. The pictures, tips and tour show your own buttons, and Settings → Device lets you try them. |
| **Make it yours** | 12 themes (including a see-through one), 16 highlight colours plus a custom colour you shape with Colour, Strength and Brightness (always readable), four highlight styles, three key shapes, two layouts, three sizes, a number row, and Xbox, PlayStation or Switch button icons. |
| **Nine sound packs** | Soft, Thock, Tactile, Typewriter and Felt for real-key sounds; Chime, Glass, Pop and Retro for little notes in one scale. Vibration is tuned so even small motors are felt. |
| **Fits every screen** | Wide, 4:3, portrait or split screen. The keyboard never covers more than 60 % of the screen. |
| **A Settings app for the controller** | Six tabs, each about one thing. A live keyboard preview (press Y), the tour, a short What's new after each update, and your highlight colour all through it. |
| **Types straight into the app** | No full-screen typing box covering your game or launcher. |

## Controls

<p align="center"><img src="docs/images/pack/controls.png" alt="The full button map" width="100%"></p>

<details>
<summary><b>The button map as text</b></summary>
<br>

| Button | What it does |
|-|-|
| D-pad or left stick | Move the highlight |
| A | Type the highlighted key · hold for accents |
| X | Delete · hold to speed up, then whole words |
| Y | Space |
| RT | Shift · tap twice for caps lock |
| LT | Symbols and numbers (in number boxes: letters too) |
| Start | Enter · Search · Go |
| B | Close the keyboard (the app underneath never sees it) · leave the accents, select mode or the clipboard |
| Press the left stick | Keyboard size: Normal, Large, Largest |
| Press the right stick | Take the first suggested word |
| LB / RB | Move the text cursor |
| Right stick | Move the text cursor, faster the further you push · up and down: a line, or the start or end |
| Select (View · Create · Minus) | Select text · press twice for everything |
| RT + LB / RB or RT + right stick | Jump a whole word |
| RT + A | One capital letter |
| RT + Y | Accent the last letter (e → é → è …) |
| RT + X | Undo |
| RT + LT | Switch language (when you've picked more than one) |

Settings → Button guide shows the same list in your controller's own icons, and leaves out anything you've switched off.

</details>

## Questions

### How do I type with a controller on Android?

Install Inlay, turn it on in **About → Turn on Inlay** and make it your keyboard with **About → Switch keyboard**. From then on, every text box opens Inlay: move with the D-pad or left stick and press A to type. Y is space, X deletes and Start is enter.

### Does it work on my gaming handheld?

It's made for them. Automatic recognises handhelds from AYN (Odin), Retroid, Anbernic, AYANEO, GPD and others, including vertical ones, and shows your own buttons. Anything with Android 10 or newer and built-in controls or a connected gamepad works.

### Which controllers work?

Anything Android sees as a gamepad: built-in handheld controls, and USB or Bluetooth controllers. Inlay can show Xbox, PlayStation or Nintendo Switch button icons.

### Can I use it on Android TV or Google TV with the remote?

Yes. TV mode turns on by itself: a compact keyboard floating over the app with a big line showing what you type. OK types and Back closes, no controller needed.

### Does it work in my launcher, emulator or game?

It works in any app that opens Android's keyboard. A game that draws its own on-screen keyboard instead won't call it.

### Is it safe for passwords?

Inlay has no internet permission, so nothing you type can leave your device through it. It never learns words from password, web address or email boxes, and clipboard copies marked as sensitive are never kept.

### Is it really free?

Yes. No ads, no in-app purchases, no account. The code is open source under GPL-3.0.

## Tested on

| Device | Android | Result |
|-|-|-|
| AYN Odin 2 Portal | 13 | Everything tested |
| AYN Odin 3 | 15 | Works ([reported by a user](../../issues/1)) |
| AYN Odin 3 Pro | – | Works (reported by a user on Reddit) |

Tried it on something else? Please [tell me how it went](../../issues/new/choose), even if it worked perfectly. That's how this list grows.

## Who made this

I'm Emre, and I don't write code. I want to say that plainly: every line of this app was written by Claude, Anthropic's AI. My part was the idea, deciding how it should look, feel and sound, and testing it on my AYN Odin 2 Portal, over and over, until it felt right. The sounds were made by Claude from scratch too, modelled on button sounds I picked out.

I'm sharing it to give something back to the community. There's no catch and nothing is for sale.

Since I can't read the code myself, I can't promise it's flawless, and I can only test it on my own device. If something goes wrong on yours, please open an issue and tell me what happened. If you can read code, improvements are very welcome.

## Build it yourself

Open the project in Android Studio and press **Run**. No extra libraries are needed. Before sending a change, have a look at [the update checklist](docs/UPDATING.md).

## Brand and credits

* The icon, colours and type are in [docs/brand](docs/brand/BRAND.md), with the rules for using them.
* Fonts: [Atkinson Hyperlegible Next](https://github.com/googlefonts/atkinson-hyperlegible-next) and [Inter](https://github.com/rsms/inter), both under the SIL Open Font Licence (see `licenses/`).
* The PlayStation controller drawing is traced from ["Dualshock 4 Layout.svg"](https://commons.wikimedia.org/wiki/File:Dualshock_4_Layout.svg) by Tokyoship, [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/), redrawn in Inlay's line style. The Xbox and Switch Pro drawings are traced from reference pictures, logos left out. Controller names belong to their makers.
* All sounds were made for this project from scratch. `tools/sounds.py` makes them and `tools/soundcheck.py` checks them (rules in `docs/brand/SOUND.md`).
* The 1.1 icon refresh followed the testing checklist from [logo-design-skill](https://github.com/kaankiziltug/logo-design-skill).

## Licence

GPL-3.0. See the [`LICENSE`](LICENSE) file.
