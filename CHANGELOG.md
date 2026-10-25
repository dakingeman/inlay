# Changelog

## 1.1.0 — Languages, themes, smart keys and a TV mode

The first big update, shaped by the comments on GitHub and Reddit. Thank you to everyone who tried 1.0 and wrote back.

**Typing**
- **11 languages:** English, Deutsch, Français (AZERTY), Español, Português, Norsk, Dansk, Svenska · Suomi, Türkçe, Русский and Українська, each with the letter layout people know from their phone. Every letter is one column wide, so up and down still go straight. Pick them in Settings → Typing.
- **Quick language switch:** pick up to three languages and switch between them while typing with RT + LT. The space bar shows which one is on (you can turn that off for a clean space bar; a switch still shows the name for a moment).
- **Accents without a touch screen:** type a letter, then RT + Y turns it into its accented forms, one press at a time (e → é → è → ê …). Each language offers its own accents first, so Italian, Dutch, Polish and others work on the English layout.
- **Smart keys:** web address boxes get / and .com, email boxes get @ and .com, and number boxes open a big number pad. LT goes round the number pad, letters and symbols, so every character is still reachable. Can be turned off in Settings → Typing.
- **TV mode:** when your handheld is docked to a TV (or on Android TV), the keyboard turns into a compact floating panel with a big line on top that shows what you're typing, so you can read it from the sofa instead of hunting for the app's small text box. It stays clear of the screen edges, leaves the game visible around it, has a stronger highlight, and shows dots in password boxes. Settings → Typing → TV mode: Automatic, On or Off. Inlay also installs on Android TV and Google TV.
- The symbols page has a local money sign (€, £, ₺, ₽ or ₴).

**Look and sound**
- **Four new themes:** Light, Outline (key borders), Wallpaper (colours from your wallpaper, Android 12+) and **Clear**, a see-through theme with a See-through slider. Thanks to the GitHub user who asked for a transparent one. Clear shows the app behind wherever that app sits under the keyboard; apps that move their content up out of the way show their own background there.
- **Three new sound packs:** Retro (short 8-bit blips), Typewriter (a metal strike, with a small bell on Enter) and Felt (very soft taps for playing at night). Seven packs in all, with the same meanings in every one. Settings describes each pack and plays a short sample while you browse them.
- Sounds asked for while a pack is still loading now play as soon as it's ready, instead of being skipped.
- **Refreshed icon:** the same little keyboard, redrawn so round icon masks never clip it, with clearer gaps at small sizes. The icon's teal is now the default highlight colour too.

**Settings**
- **A one-minute tour** the first time you open Inlay: the buttons and combos, with the keyboard acting each one out. Skip it with Start, replay it from About.
- **Set-up card:** until Inlay is switched on and chosen as your keyboard, Settings opens with two steps and one button for the next one.
- **Your colour, everywhere:** your highlight colour now runs through Settings (tabs, focus ring, options, switches, sliders).
- **New Typing page** with languages, smart keys, keyboard size, stick speed, shortcuts and hints. Look keeps themes, colours and style.
- The button guide lists every combo, including the new ones, and only shows what's switched on.
- **Clearing clipboard history can be undone** in the same row.
- **Get updates automatically:** About → Get updates automatically adds Inlay to Obtainium in one step.

**Fixed**
- Swiping Inlay's Settings away from recent apps could stop the keyboard or make Android switch to another keyboard. Some handhelds force-stop apps you swipe away, and Android then drops that keyboard. Settings now never appears in recent apps and closes itself when you leave it.
- Chat and search apps that restart the text box after Send or Go no longer reset the keyboard to the middle key.

Your settings and clipboard history carry over from 1.0.0. Code written by Claude; directed and tested by me on an AYN Odin 2 Portal.


## 1.0.0 — First release

The first public version of Inlay: a controller-first keyboard for Android handhelds.

### What's in it

**Typing with a controller**
- A grid keyboard you move with the D-pad or left stick. Holding a direction repeats and speeds up; the stick has a calm dead zone so it never skips keys.
- Up and down remember your column, so moving through the rows feels natural.
- A types, X deletes, Y is space, Start is enter, B closes.
- X held down: deletes faster and faster, then whole words.
- LB / RB move the cursor. Hold RT and press them to jump whole words.
- RT: tap for shift, tap twice for caps lock. Hold RT for extra moves: a capital letter, undo (RT + X), word jumps. Small hints show what's possible while you hold it.
- LT switches between letters and symbols.
- Select: start selecting text. Press twice to select everything; press again to deselect.
- Double-tap Y (space) for a full stop (optional).
- Clipboard history with up to six recent copies. Anything marked as a password is skipped.
- Always types straight into the app's own text field; Android's full-screen typing box never appears.

**Look**
- OLED-friendly design: pure black, one accent colour, high-contrast letters picked automatically.
- 16 highlight colours plus a custom colour wheel. 8 key colour themes.
- Four highlight styles: Glow, Solid, Outline and Lift. Three ways for the highlight to move: Instant, Subtle and Fluid.
- Three key shapes, two layouts (Grid and Classic), three sizes (press the left stick).
- Controller icons in Xbox, PlayStation or Switch style, everywhere in the app.
- Choice of font: Atkinson Hyperlegible Next (made for clear letters), Inter, or the system font, with adjustable letter weight.

**Sound and vibration**
- Four sound packs: Soft (default), Soft Deep, Tactile and Chime. Every sound was made for this app.
- One action = one sound and one vibration pulse. Bigger actions feel weightier, never louder-by-repetition.
- Vibration starts every pulse with a short full-power kick, so even small motors are felt. Works in silent mode.
- Separate strengths for moving and pressing, a volume setting, and an option to play sounds in silent mode.

**Settings app**
- Fully usable with the controller: LB / RB switch sections, the D-pad changes values, Y shows a live keyboard preview.
- Uses the same sound pack as the keyboard, so the whole app sounds like one thing.
- Built-in button guide.

**Every device**
- Works on Android 10 and newer.
- Adapts to wide, 4:3, portrait and split-screen layouts.

### How it was made

Inlay grew over eleven rounds of design, testing and feedback on an AYN Odin 2 Portal, from a first rough grid to what you see here. Along the way:

- The D-pad and stick input was rebuilt so the keyboard never interferes with the rest of the system.
- The design was rebuilt around a written design guide (`DESIGN_GUIDE.md`) with shared spacing, corners, motion and contrast rules.
- The sounds went through several complete redesigns, ending with a recipe modelled on real soft button presses: a short low thud plus a crisp click, gone within 15 ms.
- Vibration was re-tuned for small handheld motors.
- The full-screen typing box some apps trigger was locked out, so you always see the app you're typing into.
