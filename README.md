<p align="center"><img src="docs/images/pack/hero.png" alt="Inlay: a gamepad keyboard for Android handhelds"></p>

**A gamepad keyboard for Android handhelds.** You drive it with the controller, not your thumbs on the glass.

Move with the D-pad or stick, press A to type, and never reach for the touchscreen again. Made for handhelds like the AYN Odin and Retroid Pocket, and for any Android device with a gamepad connected.

Free, open source, no ads, no tracking, no internet permission.

[!\[Latest release](https://img.shields.io/github/v/release/dakingeman/inlay?color=72D6E8\&labelColor=0B0B0E)](../../releases/latest) [!\[Licence](https://img.shields.io/github/license/dakingeman/inlay?color=72D6E8\&labelColor=0B0B0E)](LICENSE) !\[Android 10+](https://img.shields.io/badge/Android-10%2B-72D6E8?labelColor=0B0B0E)

[**Download the latest version**](../../releases/latest) · [How to install](#install) · [Report a problem](../../issues/new/choose)

!\[Inlay in 20 seconds](docs/images/teaser.gif)

*Drawn from the app's real layouts, colours and sizes.* [*Watch the full video (1:20)*](VIDEO_LINK) *· a real screen recording from my Odin 2 Portal is* [*here*](docs/images/demo.gif)*.*

## New in 1.1

Languages, four new themes (including a see-through one), smart keys, a TV mode for docked play, seven sound packs and a one-minute tour. The full list is in the [changelog](CHANGELOG.md).

## Who made this

I'm Emre, and I don't write code. I want to say that plainly: every line of this app was written by Claude, Anthropic's AI. My part was the idea, deciding how it should look, feel and sound, and testing it on my AYN Odin 2 Portal, over and over, until it felt right. The sounds were made by Claude from scratch too, modelled on button sounds I picked out.

I'm sharing it to give something back to the community. There's no catch and nothing is for sale.

Since I can't read the code myself, I can't promise it's flawless, and I can only test it on my own device. If something goes wrong on yours, please open an issue and tell me what happened. If you can read code, improvements are very welcome.

## What it looks like

|||
|-|-|
|!\[Move with the D-pad, type with A](docs/images/pack/typing.png)|!\[Edit text properly](docs/images/pack/editing.png)|
|!\[Hold RT for more](docs/images/pack/hold.png)|!\[Clipboard history](docs/images/pack/clipboard.png)|
|!\[Make it look how you want](docs/images/pack/looks.png)|!\[Icons for your controller](docs/images/pack/icons.png)|
|!\[Eleven languages](docs/images/pack/languages.png)|!\[Smart keys](docs/images/pack/smart-keys.png)|
|!\[Seven sound packs](docs/images/pack/sound-packs.png)|!\[TV mode](docs/images/pack/tv-mode.png)|
|!\[A one-minute tour](docs/images/pack/tour.png)|!\[Settings, in your colour](docs/images/pack/settings.png)|

\---

## Features

* **Made for controllers.** D-pad or left stick moves the highlight, with smart repeat when you hold a direction.
* **Every button does something useful.**

  * A types, X deletes (hold it and it speeds up, then deletes whole words), Y is space, Start is enter.
  * LB / RB move the cursor. Hold RT with them to jump whole words.
  * RT taps shift; tap twice for caps lock. LT switches to symbols.
  * Select starts selecting text; press it twice to select everything.
  * Hold RT and small tips appear on the keyboard showing what else you can do.
* **11 languages, switch with RT + LT.** English, German, French (AZERTY), Spanish, Portuguese, Norwegian, Danish, Swedish/Finnish, Turkish, Russian and Ukrainian. Pick up to three. RT + Y adds accents (é, ñ, ł and more), so Italian, Dutch, Polish and others work on the English layout.
* **Smart keys.** / and .com in web addresses, @ in email boxes, a big number pad for numbers.
* **TV mode** for a docked handheld: a compact floating keyboard with a big line showing what you type, readable from the sofa. Turns on by itself when you dock, and it installs on Android TV and Google TV.
* **Clipboard history.** Your last few copies, one button away. Passwords marked as sensitive are never kept.
* **Types straight into the app.** No full-screen typing box covering your game or launcher.
* **Sound and vibration that feel premium.** Seven sound packs (Soft, Soft Deep, Tactile, Chime, Retro, Typewriter, Felt) and vibration tuned so even small motors are felt.
* **Make it yours.** 12 themes (8 OLED-friendly dark ones, Light, Outline, Wallpaper colours and a see-through Clear theme), 16 highlight colours plus any custom colour, four highlight styles, three key shapes, two layouts, three sizes, and controller icons in Xbox, PlayStation or Switch style.
* **Fits every screen.** Wide, 4:3, portrait or split screen: the keyboard scales itself so it never covers more than 60 % of the screen.
* **A Settings app you can use with the controller**, with a live keyboard preview (press Y), a one-minute tour, and your highlight colour all through it.

## Requirements

* Android 10 or newer
* A built-in controller or a connected gamepad

## Tested on

|Device|Android|Result|
|-|-|-|
|AYN Odin 2 Portal|13|Everything tested|
|AYN Odin 3|15|Works ([reported by a user](../../issues/1))|
|AYN Odin 3 Pro|–|Works (reported by a user on Reddit)|

Tried it on something else? Please [tell me how it went](../../issues/new/choose), even if it worked perfectly. That's how this list grows.

## Install

1. Download the latest `.apk` file from the [Releases](../../releases) page onto your device.
2. Open it and allow installing from this source when Android asks.
3. Open **Inlay** and choose **About → Turn on Inlay**.
4. Choose **About → Switch keyboard** to make it the active keyboard.

### Get updates automatically

The free [Obtainium](https://github.com/ImranR98/Obtainium) app can watch this page and install new versions for you. In Inlay, choose **About → Get updates automatically**, or on your device tap [add Inlay to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/dakingeman/inlay).

### On Android TV or Google TV

Install the same `.apk` (for example with a file manager or "Send files to TV"), open **Inlay** from the apps row and follow the set-up card. TV mode turns on by itself there.

## Build it yourself

Open the project in Android Studio and press **Run**. No extra libraries are needed.

## Brand

The icon, colours and type are in [docs/brand](docs/brand/BRAND.md), with the rules for using them.

## Credits

* Fonts: [Atkinson Hyperlegible Next](https://github.com/googlefonts/atkinson-hyperlegible-next) and [Inter](https://github.com/rsms/inter), both under the SIL Open Font Licence (see `licenses/`).
* All sounds were made for this project from scratch (the scripts that made them are in `tools/`).
* The 1.1 icon refresh followed the testing checklist from [logo-design-skill](https://github.com/kaankiziltug/logo-design-skill).

## Licence

See the `LICENSE` file.

