# Changelog

## 1.2.0 — Words, accents, sounds and the device in your hands

The biggest update so far. Inlay now suggests words and learns yours, puts accents one hold away, tidies small slips as you type, and knows what you're holding: a controller, a handheld, a vertical handheld or a TV remote. All nine sound packs were remade, Settings got a clearer layout, and the tour and What's new got a new look in your own colour.

**Typing**
- **Word suggestions.** While you type, the words you probably mean show in the top row, the most likely first. Press the right stick in to take the first one, or move up and pick another. Word lists for all 11 languages. Settings → Typing.
- **Learn my words.** Words you use often move up the suggestions, and new ones like names and game titles join once you've used them in three different places. It's careful on purpose: a word only counts once you've moved on to the next one (so typos you fix are never kept), mashed keys and words with numbers are ignored, and words fade if you stop using them. On this device only, never from password, web address or email boxes or from apps that ask keyboards not to learn. Forget my words clears it, with an undo.
- **Hold A for accents.** Hold A on a letter and its accents rise into the top row. Move to one and let go, or let go first and they stay open until you choose. RT + Y still works too.
- **Tidies up as you type.** A space typed just before a full stop, comma, ! or ? moves after it ("word ." becomes "word. "), and in English a lone i becomes I. Both have a switch, and so do automatic capitals now.
- **Number row.** An optional slim row of 1 to 0 above the letters. Settings → Look. Number boxes get a tidier number pad too.
- **B closes the keyboard, and only the keyboard.** The app underneath never sees that press, so a browser no longer goes back or closes when you just wanted the keyboard gone. Press B again and the app gets it as usual.

**Made for what you hold**
- **A picture of your device** in the Button guide, the tour and What's new, lighting up the buttons as they're used. Every pressed button, D-pad arm included, gets a ring that follows its shape; hold it and a second ring runs round, so a hold is clear at a glance. Sticks tilt inside their well with an arrow on top, and a clicked stick sinks in.
- **New Device tab** in Settings: what you're holding (Automatic says why it chose it), your button icons, and Try your buttons, which lights up every press on the picture. Hold B to stop.
- **Automatic knows gaming handhelds by name**: AYN (Odin), Retroid, Anbernic, AYANEO, GPD and others. Many of them report their own controls as a plugged-in controller, so an Odin used to show up as a controller. The Retroid Pocket Classic is recognised as a vertical handheld without sticks, the Anbernic RG405V and RG406V as vertical with sticks. Anything else still goes by its controls and screen shape.
- **TV remotes.** OK types, Back closes, and the ring moves. The tour, What's new and Settings all work with just a remote, with their own pages, and controller hints are hidden.
- **Vertical handhelds**, with or without sticks. Without sticks, stick tips and features are hidden everywhere. Small 4:3 and square screens get layouts that fit.
- The Button guide shows held buttons in a HOLD capsule, so "hold RT, press A" is clear at a glance.

**Sound**
- **Nine sound packs, all remade.** Soft (still the default), Thock, Tactile, Typewriter and Felt sound like real keys. Chime, Glass, Pop and Retro play notes from one musical scale, so nothing ever clashes. Thock replaces Soft Deep (if you used Soft Deep, you now get Thock), and Glass and Pop are new.
- **Every pack has its own place.** Soft is a little warmer, Thock's knock a step deeper, Felt padded and muffled like typing through cloth, and Retro brighter, an octave up, so no two packs sound alike.
- **Every sound was checked** against the same rules: it starts the instant you press, plays once and gets out of the way, never sounds sharp or piercing, and keeps its place on headphones, a handheld speaker and a phone speaker. Caps lock no longer gets quieter than a letter on small speakers.
- **Four sounds for every letter in every pack**, so fast typing never sounds like a machine gun. Tactile and Chime had only one.
- **A more even volume slider.** Every step is the same change in loudness, and the default leaves room to go louder.
- **Sounds play in silent mode on handhelds and TVs.** They're often on silent with no phone to ring, so the keyboard seemed broken. Phones still respect silent mode, and if silent mode is muting the sounds, Settings says so. If you'd already chosen, your choice stays.

**Look**
- **A custom colour you can really shape:** Colour, Strength (soft to vivid) and Brightness sliders, shown once you pick Custom. Every combination stays readable on every screen, and a custom colour from 1.1 looks the same after the update.
- **The tour and What's new have a new look:** a dark graphite background with a soft glow made from your highlight colour, any colour, presets and custom alike. The tour says Welcome tour; What's new wears a New in 1.2.0 tag and its light comes from the other side, so the two are easy to tell apart.
- **New device pictures:** flat, with one clear outline, the same in the app and on GitHub. Bumpers and triggers are the same size on every device, the Xbox grips end round like the real thing, face buttons are bigger with clearer letters, and the PlayStation button is gone (Inlay doesn't use it). Rings size themselves to the room round each button, so they never touch anything, at any size.
- **Cleaner highlight and tidier screens.** The ring around the chosen key, clipboard card or Settings tab now sits on its own edge, so it never touches its neighbours, in every highlight style. The accent tray keeps the same gap to the keys as every other row. Button badges follow each controller: Switch shows + and −, PlayStation shows Create, and Xbox's View icon matches the real one. On narrow keys the shift, delete and enter icons step aside so the badge never covers them. On small screens Settings gives the page its full width, so no caption is cut off, and set-up steps, credits and the TV text box read properly everywhere. The HOLD tag and the NEW IN tag keep your colour but lose their rings, so only the thing you're on wears a ring.

**Settings**
- **Six tabs**, each about one thing: Device, Look, Typing, Sound & haptics, Button guide and About. Clipboard is now part of Typing.
- **Settings opens without the keyboard.** Press Y to slide it up, and the row you're on stays above it, whole.
- **With the keyboard up, the tabs fold into a slim line of bars** that still shows which section you're in. LB and RB move along it.
- The tabs move as one when you switch quickly, and tabs that don't fit slide and fade at the edges.
- With a remote, the tabs take the focus and there's a keyboard key at the end, since a remote has no Y.

**Tour and What's new**
- **What's new has a page for the sound packs:** each pack types its own name in its own sounds while its name pops up.
- The welcome page shows every kind of device Inlay knows, yours first.
- The demo keyboard sits in its own window and keeps the same key shape on every page; the words above give way first when a page is full.
- A loop bar under every looping demo shows when it starts again.
- Remotes get OK and Back in the bottom bar.

**Faster and lighter**
- Demos stop the moment they're out of sight, instead of running on in the background.
- Device pictures are drawn once and reused, and the tour's background is painted once instead of on every frame.
- Calmer redrawing on fast screens: slow animations no longer redraw 120 times a second on a 120 Hz screen. Same look, less battery.
- Leftover code and two unused libraries removed, so the app is smaller.

**Privacy**
- Your clipboard history and learned words are never included in Android's cloud backups.

**Small adjustments**
- The text cursor stays put at the start and end of the text, instead of moving the app's focus away (which closed the keyboard).
- Slimmer bumpers on the vertical handheld picture; Start and Select sit on top of the wide handheld.
- The two vertical handhelds change into each other in place on the welcome page.
- The tour copies before it opens the clipboard, and the demo cursor sits where the real one would.
- The tour's demo steps light one button at a time.

**Fixes**
- Pressing a stick in no longer acts as A in Settings and the tour.
- Scrolling back to the top of a Settings page shows the first row whole again.
- Settings rows are never cut off at the edge of the screen; a hidden row glides into view.
- Button badges are never cut off: their edge is now drawn inside the badge, so nothing that sizes itself to a badge can clip it (it could lose a sliver on its left side).
- The keyboard preview in Settings slides in as a panel, with no ghost image behind it.
- Your highlight colour never leaks into a page that should show another.

**Behind the scenes** (for anyone reading the code)
- Every picture and video on the GitHub page is drawn by the app's own code, so they always match the real app. The rules are in `docs/visuals-src/VISUAL_SYSTEM.md`.
- The sounds are made by `tools/sounds.py` and checked by `tools/soundcheck.py`; the rules are in `docs/brand/SOUND.md`.
- More automatic checks: every tour and What's new page on every device, the keyboard sliding in and out under button mashing, every custom colour for readability, and the sound packs against their files.
- `tools/changeset.py` keeps work done in parallel from overwriting each other.

## 1.1.1 — Faster, and the right stick joins in

A speed update. Moving around and typing should now feel instant, even when you type fast, and the right stick finally has a job. It's also the first update to say hello: a short What's new screen shows the highlights.

**Speed**
- **No more waiting behind the app.** The keyboard used to ask the app about its text up to five times after every letter (for automatic capitals and the TV line), and every button press had to wait for the answers. It now asks once, when you pause, and only reads the TV line in TV mode.
- **The highlight keeps up with you.** It now lands most of the way on the next key on the very first frame, instead of sliding there.
- **Smoother drawing.** The chosen font used to be rebuilt for every key on every frame. It's now built once.
- **Clicks and buzzes run on their own.** Sounds and vibration start on a separate helper, so they never hold up the next press.
- **Holding a direction repeats sooner and faster**, and so do holding X (delete) and LB / RB (cursor). The triggers react to a lighter press.

**New**
- **The right stick moves the text cursor.** Push it sideways to move, and the further you push, the faster it goes. Hold RT for whole words. Up and down go a line in long text, or to the start and end of short boxes. In select mode it grows the selection. Inlay checks where your controller keeps its right stick, so it never confuses it with a trigger. On by default; turn it off in Settings → Typing. It's in the Button guide too.
- **What's new.** The first time you open Settings after an update, a short screen shows its highlights, acted out on the keyboard like the tour, with your button icons and colour. Once per update, never on the keyboard itself, and new users get the tour instead. See it again in About, next to a link to the full changelog.

**Better**
- **The left stick is more precise.** It reacts to a lighter push, has no dead spots on the diagonals, and quick left-right flicks all land now (the spring-back filter used to swallow some). Held repeats follow how far you push: a light push moves gently, a full push at your Stick speed. Medium now matches the D-pad.
- **Tour pages always fit.** On shorter screens (like a 1080p handheld), the last rows of a long tour page sat below the edge where the controller couldn't reach them. The words now shrink a step until every row shows, and up and down scroll as a backup.
- **Respects "Remove animations".** With Android's accessibility setting on, the tour and What's new show still pictures instead of moving ones.
- **Capitals after a full stop in more apps.** Some apps, like the Play Store search, never ask for capitals. Inlay now starts a new sentence with a capital there too, after . ! or ? and a space. Never at the very start of a box, and never in web address, email or password boxes.

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
