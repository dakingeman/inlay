# Word lists (word suggestions)

`app/src/main/res/raw/words_*.txt` are the word lists behind word suggestions, one per
language, most common word first.

## Sources and licences

- **Word frequencies:** [FrequencyWords](https://github.com/hermitdave/FrequencyWords) by
  Hermit Dave (the 2018 lists), built from [OpenSubtitles 2018](https://opus.nlpl.eu/OpenSubtitles.php).
  Licensed **CC BY-SA 4.0**: <https://creativecommons.org/licenses/by-sa/4.0/>
- **English:** the cleaned list from [Inlay-Revised](https://github.com/haneeshraja/inlay-revised)
  by haneeshraja (FrequencyWords, checked against a dictionary, offensive words removed,
  contractions put back). CC BY-SA 4.0.
- **Swedish · Suomi:** the Swedish and Finnish lists, taken in turns.

## Changes made (all languages except English)

- Kept words of 2 to 24 letters that can be typed on that language's keyboard.
- Removed words found in the [LDNOOBW](https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words)
  lists (CC BY 4.0). There's no Ukrainian list there, so Ukrainian isn't filtered yet.
- Kept the 15,000 most common (Swedish · Suomi: 9,000 of each).

The word list files are CC BY-SA 4.0, the same as their source. The rest of the app is under
the licence in `LICENSE`. CC BY-SA 4.0 material can be used in a GPL-3.0 project.

Known gaps in this test version: the lists aren't checked against dictionaries yet (except
English), so a few names can show up, and German nouns come in lower case.
