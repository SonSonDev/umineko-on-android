# Purpose of this document

This tutorial is here to teach you, specifically, to mod an 07th-Expansion game.

If you are looking to create a new game with ponscripter, this will not help you. Nothing will. The [API docs](/ponscripter-fork/api/ ':ignore') will only make things worse. Just stop. Please.

If you are looking to modify a different ponscripter game, information here may be somewhat inaccurate, as it's written with Ryukishi's programming oddities in mind.

# The basics

ponscripter is the engine that 07th-Expansion has used to release all its games in the West so far, with the sole exception of Higurashi &mdash; that used a custom Unity-based engine. Some of the games that weren't officially released used an older iteration of the engine &mdash; ONScripter &mdash; but we don't expect that those older games will ever receive console releases, so they are largely irrelevant. We expect that future 07th-Expansion games will keep using ponscripter into the foreseeable future.

The [current version of ponscripter](https://github.com/07th-mod/ponscripter-fork) is maintained by the [07th-Mod](https://07th-mod.com) team.

A [full API documentation](/ponscripter-fork/api/ ':ignore') is available as well, but it is somewhat difficult to parse and contains a lot of knowledge that you likely won't ever need when working on a mod.

## File encryption

### Script encryption

All Ryukishi games use a single script file called `0.utf` (a plaintext file). The 07th-Mod version of the engine modifies this filename to `0.u` instead, to prevent Steam upgrades from breaking scripts. If you cannot find a `0.utf` or a `0.u` in the game folder, you will likely instead see a `pscript.dat`. This is a trivially obfuscated script, and can be decoded with `nscdec` (rename `result.txt` to `0.u`).

**There is no need to ever re-obfuscate the script**, as the game will work fine with the plaintext script, and always prefer the plaintext script over the encrypted one.

### Resource encryption

Even though ponscripter can load files directly from disk, games commonly use encrypted `arc*.nsa` files. These files contain archived resources (images etc), and can be extracted with `nsadec`.

**There is no need to ever re-pack the NSA archives**, as the game will work fine loading files from disk, and will always prefer the files on disk to the files in the `arc*.nsa` archive.

### Decryption Tools Downloads

`nscdec` and `nsadec` can be downloaded [here](/ponscripter-fork/onscrtools.zip ':ignore').

## Debug mode

While developing, you'll often want to start ponscripter with the `--debug` flag. This is because it normally just completely ignores a lot of things that would be considered errors by other engines, making it nigh-impossible to figure out why something doesn't work, or works in a weird way. On Windows, starting the engine with `--debug` will enable logging to files in `%APPDATA%\ponscripter`, which you can then read to figure out what went wrong if something did.

## Editing the files

Due to the size of the game scripts, you will need a text editor which can handle large files. [VS Code](https://code.visualstudio.com/) works quite well, and works with [our syntax highlighter](https://github.com/07th-mod/script-syntax-highlighters). If you are looking for something more lightweight, [Notepad++](https://notepad-plus-plus.org/) also does the job.

## Comments

ponscripter's comments start with a `;` and last until the end of the line. There are no block comments. Note that three special commands start with a `;` too: `;value`, `;mode` and `;gameid`, and are only usable at the very start of a script. Those are *not* comments, even though in any sane person's mind they would be.

## Command separation
Commands in ponscripter are separated with a newline, or with a `:` (that's a *colon*, not a semicolon &mdash; those are comments).

# Text display

A lot of work will likely involve editing text. ponscripter's text display is *slightly* insane. Any Japanese text will just be displayed as-is in most contexts, so a Japanese text line will mostly just consist of the text itself and of control characters (see below). For English, on the other hand, you need to use the "text mode". The text mode is entered whenever a `^` character is encountered, and exited upon encountering a clickwait/pagewait. A closing `^` is optional, but highly recommended as it makes text significantly easier to parse. In other words, English text usually looks like a weird quoted string:

```
…お酒を嗜まれましたな？ ; Japanese text example
^  ...So, you still haven't overcome your love of alcohol?"^ ; English text example
```

## Control characters

Text lines will tend to be interspersed with a few control characters that alter how text display works. There's a few of those that are relevant here.

- `@` will insert a "clickwait". This will wait for a user to click their mouse button or otherwise advance the text. After the click, the text will proceed without clearing the screen. If the line in the script ends with a `@`, a paragraph break is automatically inserted in the displayed text as well.
- `\` will insert a "pagewait". This is the same as above, except the screen is cleared of text after the click as well.
- `@/` is the same as `@`, except a paragraph break is never automatically inserted.
- `~` is for text tags such as bold, italic, etc. The syntax you'll see most usually is `~ib~some text~ib~`, which renders as ***some text***.
- `#` is for colours. These are just your usual HTML colour codes. `^#ff0000You are incompetent!#ffffff^` will render as `You are incompetent!` in red, switching back to white for the rest of the text. Note that colour *names* are not supported: `#red` will not do anything.
- If a line ends with no clickwait at all, or just `/` (which prevents a paragraph break), then the game will just continue to whatever commands are on the next line without stopping. This is usually used to play a sound or show an effect mid-sentence.

Be very, very careful with the `~` character when translating, especially when porting translations over from ONScripter (as it didn't have `~` as a special character). It might be tempting to use it to express playfulness or something similar to that, but it will also crash the engine. If you want to actually render a `~` in your text, double it, i.e. write `~~`. These mistakes tend to be hard to catch, as the line will look just fine at a glance, so I suggest just avoiding this cursed snake altogether.

The explanation about newlines might not make much sense without seeing it in action. So, let's give a few examples:

```
^A^@^B^@^C^ ; This will result in "ABC" after all clickwaits are triggered
^A^@
^B^@
^C^@ ; This will result in "A B C" (with linebreaks instead of spaces)
^A^@/
^B^@/
^C^@/ ; This will have the same effect as the first line
```

The below example does not have a clickwait on the first line (just a `/` to suppress a paragraph break). It does the following:

- Show the text "Stop!" on the screen, without a newline
- Immediately shake the screen
- Show the text "...you didn't stop..." on the screen
- Wait for the user to click to advance to the next page

```
^Stop!^/
quakey 4,800
^...you didn't stop...^\
```

## Language support
ponscripter is built as an engine with bilingual games in mind: in other words, the same script will traditionally house text for both Japanese and English (adding more languages is, unfortunately, impossible). In games that use this feature, Japanese lines should be prefixed with `langjp` and English lines should be prefixed with `langen`. The text in these lines will only be displayed when the user has the appropriate language selected, although do note that any commands unrelated to text display will *not* take `langen` and `langjp` into account, and trigger for both languages.

## Voices
Voices are generally played with the `dwave` command. There is a multi-lingual version of that command that we created for our mod: `dwave_eng` will only play on `langen` lines, and `dwave_jp` will only play on `langjp` lines. If maintaining Japanese support is one of your goals, you *really* should use `dwave_jp` and `dwave_eng`, otherwise voices will turn into a mess.
The syntax of `dwave` is simple enough: `dwave channel_number,"voice_file"`. The channel number is usually set to `0`, it's only ever useful if you want to play multiple voices *at once*: in which case, use a syntax like `dwave 0,"voice_file_1":dwave 1,"voice_file_2":dwave 2,"voice_file_3"`

## Putting it all together... (text display)

Now that we've covered all of *that*, we can finally read some of Umineko's script! Let's give it a go!

```
langjp「この馬鹿がッ！！@　決まっておろうがッ！！@　殺したらそなたが歪めるだろうその表情が楽しいからの他に何の理由が必要なのかッ！！@　くっひゃはははははははは！！」\
langen:dwave_eng 0, "voice\27\50700001.ogg":^"You idiot!!^@:dwave_eng 0, "voice\27\50700002.ogg":^  Isn't it obvious?!!^@:dwave_eng 0, "voice\27\50700003.ogg":^  It'll be fun to kill her and see your face twist in pain, why else?!!^@:dwave_eng 0, "voice\27\50700004.ogg":^  Gahyahahahahahahaha!!"^\
```

You should now be able to read this wihtout significant issues. Congrats, you know how to edit text!

# The first lines of the script
The first lines of the Umineko script contain the following expressions:
```
;value2500,modew540@2x,localsave=mysav
;gameid Umineko4hdz
```
All of the three bits of the first line are optional, but nevertheless important. Let's discuss them in order:

- `value2500` is the global variable border. This is too terrifying of a concept to explain just yet, so for your own safety, it'll be tackled later.
- `mode` sets the resolution. A wide variety of resolutions are supported, but only a few are actually anything a sane person would use:
  - `modew720`, for 1280x720
  - `modew1080`, for 1920x1080
  - You may also encounter `modew540@2x`, for the main Umineko games (this is also 1080p, but with the weird auto-upscale enabled, see below). It's only around for historical reasons.
-  `localsave=mysav` forces the game to save in the `mysav` folder in the game directory. This is recommended for Steam releases, as ponscripter handles Steam saves in a very weird way (see below).

The second line is just `;gameid` followed by an identifier.

- This is ignored if `localsave` is set, in which case the folder specified in `localsave` is used.
- This is also ignored when running on the Steam build of the engine *from Steam*. If `localsave` is not set, but a Steam build is used and launched via Steam, the `saves` folder will be used for saves.
- If neither of those apply, this line will make game saves go to `%APPDATA%\specified_game_id`.

# Images
## Image transparency
tl;dr: Put `force-png-alpha` into `pns.cfg` in the game root, creating the file if it does not exist.

ponscripter features three modes for image transparency: `leftup`, `alphablend` and `copy`.

- `leftup` assumes that the top-left pixel is a "sacrificed" colour: all pixels with the colours identical to the top-left one become transparent.
- `alphablend` assumes that the image is doubled in width, and its right half is an inverted alpha-mask: pixels that are black on the mask are fully opaque, pixels that are white are fully transparent.
- `copy` assumes that the image contains no transparency information at all, and is just to be drawn as-is.

The transparency mode can be set per game (with `transmode`), but modifying that is not recommended &mdash; it will, most likely, break things.

The transparency mode can also be set per image. This is done by prefixing the file path with `:l;`, `:a;` or `:c;`, e.g. `":a;myfile.bmp"`.

This implementation pre-dates support for the PNG format, and as such does not directly take into account the actual alpha channel of a PNG image.
However, if the top-left pixel of a transparent PNG is transparent, PNG transparency will be treated as expected. This is the case most of the time.
If you have transparent PNGs that have a non-transparent top-left pixel, however, transparency will break.
To fix it, add the aforementioned config parameter.

## Image sizes
Older ponscripter versions will render all images at double their resolution. This can be fixed per-image by adding `:b;` before the path (if a transparency flag is used, it becomes something similar to `:ba;` instead).
In the latest releases, this is a config option passed to `mode` on the first line of your script (see below), e.g. `modew540@2x` will enable it.
You'll usually never want to have this enabled for a modded game, unless there's historical reasons for it. Use `modew1080`. If you want to re-use some images from the base game, upscale them.

## Effects and effect numbers
When showing and hiding images, ponscripter supports a variety of visual effects. The few built-in ones are documented [here](/ponscripter-fork/api/#effect ':ignore'), and the remaining will vary per game. Ryukishi references effects by numbers instead of giving them sensible names. Unfortunately, this is something you'll have to get used to for showing/hiding custom images. You may look up how custom effects work and what they are by searching the script of your game for `effect`, and you'll most likely find the definitions quickly enough. Most of the custom effects use image-based masks, which should be relatively simple to understand.

### The `print` statement
Effect number 0 is special. It merely loads the images in memory, storing them for later usage. When you are ready to use them, call `print`, followed by an actual effect number, e.g. `print 5`. Some commands don't take effect numbers as arguments at all, and instead *require* you to use `print`.
## Backgrounds

Backgrounds are fairly trivial: `bg "filename",effect_number`, e.g. `bg "backgrounds\my.png",2`. If you use effect `0`, you will need to `print` your background.

## Sprites
Sprites are more complex. This is mainly because Ryukishi has defined custom functions that handle sprite loading and placement, and those functions will vary greatly per game. As a general rule, we'll want to avoid those custom definitions (unless we're just copying a line that already works) and instead rely on the built-in ponscripter functionality, which does the job just the same, but doesn't vary per-game. As a general rule, we will only care about four commands: `_ld` (note the underscore), `cl`, `lsp` and `csp`.

### `_ld` and `cl`
These first two are the easiest to work with: they take a "side" (`l`eft, `c`enter or `r`ight), a filename (only for `_ld`) and an effect number, e.g.:

```
_ld l,"sprites\nat\1\nat_a11_zutuu1.png",80 ; Natsuhi has headache
; ...
cl l,14 ; Natsuhi dies
```
`cl` optionally takes "a" as a side, clearing all the sprites loaded with `_ld`.

Just like before, using effect `0` will require `print`.

### `lsp` and `csp`

`lsp` and `csp` are slightly more complicated. For one, they take a "sprite number" as an argument &mdash; this can be anything as long as there's no other `lsp` statements nearby that already take this number up. You make up a number for use with `lsp`, and then pass that same number to `csp` to get rid of the sprite (you **MUST DO THIS** &mdash; do not leave these sprites hanging in memory!). These commands *must* also be followed by `print`, or they will have no effect. The syntaxes are:
```
lsp sprite_number,filename,x,y,opacity
print effect_number
; ...
csp sprite_number
print effect_number
```
The `opacity` argument is optional, and ranges from 0 to 255.

Example:
```
lsp 0,"bg\butterfly_3x.png",0,0
print 2
; ...
csp 0
print 3
```

### A note on `mld`
`mld` is a Ryukishi hack that allows the engine to display 2 sprites per side of `_ld`, with slight offsets (`mld l` is not the same position as `_ld l`, but it's still *on the left*). `mld` has largely the same syntax as `_ld`, but is fairly complex internally and will, once again, vary per game. If you want to do something similar to what `mld` does, I suggest just seeing what it does to coordinates in your game and then using the same equations for `lsp`, unless you understand exactly how `mld` works in your specific game.

# Sounds (BGM/SE/ME)
Playing sounds in Ryukishi games is a little weird, but fortunately, it's not likely you'll *ever* have to add these while modding. Still, it's worth explaining just in case.

The sounds are split into three types:
- BGM &mdash; this is music. It loops until it's stopped or replaced by another track.
- SE &mdash; these are sound effects. They play only once.
- ME &mdash; these are ambient tracks such as rain, seagull noises, etc. These loop until they are stopped or replaced, just like music, but are played *on top* of the music track.

While all the BGM/SE/ME files are properly named in the folders, the script uses numeric values to refer to them.
So, for starters, figure out what numeric value the file you want to play corresponds to &mdash; just search the script for the filename and you'll find it.
Next, pass the number to one of the functions below:

- `bgm1` or `bgm1v` for BGM,
- `se1`, `se1v`, `se2`, `se2v`, `se3`, `se3v` for SE,
- `me1`, `me1v` through `me5`, `me5v` for ME.

The numbers in the function name refer to the channel number. As you can see, the amount of channels is strictly limited, but, frankly, you should almost never need more than one per type in the first place. The `v` versions of the function allow you to also pass a volume (between 0 and 100) as a second parameter.

To stop a ME track, use `E_M1` through `E_M5`, or `E_MA` to stop all of them.
Use `E_B` to stop the BGM.

# Labels and subroutines
A label is, simply speaking, a line of the script you can jump to.
Most VN engines have these in some form or another. In ponscripter, a label is represented as a line that starts with an asterisk and is followed by an identifier:
```
*myLabel
```
You can transfer control to this label with either `goto *myLabel` or with `gosub *myLabel`.
If you use `gosub`, then it's expected that the label will have a `return` statement somewhere below it, which will return control over to the line after the `gosub` when it is encountered. In other words, the following should print "ABC":
```
^A^@/
gosub *myLabel
^C^@/
*myLabel
^B^@/
return
```
A label that is meant to be called with `gosub` is known as a subroutine. Note that subroutines do not return values or accept parameters, they are just a way to shift control back and forth. To accept parameters, you will need to use a User-Defined Command (also known as a function), which will be addressed later.

## Utilising labels for debugging
When working on a particular section of a game, save/load will sometimes not do the job, as they tend to break when the script changes. As such, you may want a quick way to jump to a particular section of the script. I suggest placing `goto *myLabel` somewhere shortly after the very start of the game, and then place `*myLabel` itself at the place you want to debug.

## `*define`, `game` and `*start`
Two labels that are always present in any ponscripter game are `*define` and `*start`.

`*start` is straightforward. The game starts there. Yay.

`*define` is *weird*. There are some commands which will only work while they are inside the `*define` label, while most others only work outside of it. `*define` is processed before script execution proper -- at "compile time", so to speak. It may help to think of everything inside `*define` as being similar to C preprocessor directives, such as `#define` (oh hey), `#include` and such others. From this point onwards, I will mention if a command has to be inside `*define`. If that isn't mentioned, you can assume it must be used *outside* of `*define`.

The `*define` label ends with a single line containing the word `game`. No `goto`s or `gosub`s.

As such, a complete (if not particularly entertaining) ponscripter game looks something like the following:
```
*define
game
*start
^Hello world!^\
```
# Definitions: constants, variables, functions
There are a variety of things that you can declare within the `*define` block. This includes string and numeric constants, variables (...sort of), and functions.
These are all used heavily throughout Ryukishi's games, and understanding how they work can help read the existing code a lot.

## Constants
Constants are declared with `stralias` (for strings) or `numalias` (for numeric constants) inside the `*define` block. The syntax for the two is similar:
```
stralias string_constant,"blah blah"
numalias num_constant,542
```
String constants are really useful for shortening often-used filenames. Numeric constants can be used to give names to effects and the like, but they have a few other uses which will be explained shortly.

## Variables
ponscripter variables are implemented in a **completely, utterly deranged** way. You have been warned.

ponscripter variables are, by default, *numbered*, not *named*. You have numeric variables, which are called `%0`, `%1`, `%2`, etc, and string variables, which go `$0`, `$1`, `$2`, etc.
Note that `%0` and `$0` are two completely different variables and you may store entirely separate values in both. 

You may want to imagine the game having two huge global arrays (one for numbers, one for strings), and you are directly indexing each one using `%` for the numeric array, and `$` for the string array.

Variables are assigned values with the `mov` command, for instance, `mov %0,1` or `mov $0,"hello"`. 

You can use indirect references: `mov %0,1` followed by `mov %%0,5` is the same as `mov %1,5`. Please do not do this. This is only for understanding it if you ever encounter it.

You may use a numeric constant to give variables human-readable names. In other words, by putting `numalias x,0` in the `*define` block, you also automatically get the two variables, `%x` and `$x`, declared for your future use. Of course, if you want to use the same number as both a meaningful constant and a meaningful variable name, you might want to do two `numalias` calls with the same number.

You cannot declare named variables at runtime -- all the names you intend to use have to be listed in the `*define` block.

Furthermore, the `;value2500` in the header defines the "border" between local and global variables.

Variables with numbers below this value are "local". That's not in the sense that they are local to a function/label that uses them &mdash; that'd make too much sense. Instead, what this means is that these variables will have their values saved only to individual save files, and they will be reset if the game is restarted.

Variables with numers equal to or above this value are "global". In ponscripter terms, this means that they will be saved to a "global save", and will maintain their value even across game restarts, unless you manually reset them.

### Ryukishi-style variable name definition
Ryukishi has come up with a clever way to define a ton of named variables without having to deal with manually setting numbers for each of them. It works as follows:
```
; Food variables:
mov %0,1
numalias potato,%0 : inc %0
numalias pineapple,%0 : inc %0
numalias orange,%0 : inc %0
; Drink variables:
mov %0,100
numalias coke,%0 : inc %0
numalias juice,%0 : inc %0
numalias beer,%0 : inc %0
; ... etc
```
Essentially, this uses an ever-incrementing (`inc` increases the specified variable by 1) numerical variable to define the numaliases themselves -- thus removing the need to manually type numbers, and allowing to group related variables together into numeric ranges, so that adding new ones in the correct place of the code is easy (well, as easy as this insane system allows...).

You don't have to use this if you don't like this approach, but at least you now have an understanding of what's happening in Ryukishi's `*define` blocks with this.

## Functions
A function, or a User-Defined Command, is declared with `defsub` followed by a label, e.g. `defsub myLabel`, inside a `*define` block. Note that the asterisk is *not* included with the label name, like it is with `goto` or `gosub`. 

After such a definition is used, writing `myLabel` on its own is equivalent to writing `gosub *myLabel`, however, you will also be able to pass additional parameters to the function now, like `myLabel 1,2,3`.

If a subroutine is called as a function with parameters, you may use `getparam` as the very first command inside the subroutine to retrieve them:
```
getparam %x,$y,%z ; three params: number, string, number
```
Function names may override built-in ponscripter commands. In this case, the original command can be accessed by prefixing it with an underscore, like `_ld`.

# Conditional execution (`if`)
The `if` statement in ponscripter is extremely straightforward at a glance:
```
if %myVar == 1 ^Hello!^@ ; Will display "Hello!" only if %myVar is equal to 1
if %myVar == 1 || %myOtherVar == 1 mov %myThirdVar,5 ; Will only execute if either of the conditions are true
if %myVar == 1 && %myOtherVar == 1 _ld l,"success.png",5 ; Will only execute if both conditions are true
```
The operators used here are exactly what you'd expect: `==`, `!=`, `<`, `>`, `<=`, `>=`, `&&` and `||`.

However, there are a few caveats to keep in mind. 

For starters, the comparison itself is *required* -- you cannot merely write `if %myVar`.

Secondly, you may not mix `||` and `&&` in the same if-statement. It's either one or the other. If you want a more complex combination, multiple `if` statements might be required, but it's unlikely you'll run into such a thing in a VN.

Finally, `if` only accepts a single statement after it... well, sort of. It actually affects the entire rest of the line, so:
```
if %myVar == 1 dwave 0,"myvoice.ogg":^Hello!^@:dwave 0, "myothervoice.ogg":^And goodbye!^@
```
All of this will only execute if `%myVar == 1` -- it will affect both the voices and both text lines, not just the first voice line.

Still, sometimes the actions you'll want to perform are too awkward to fit on a single line. Unfortunately, ponscripter's `if` does not support blocks.

There are two ways of handling this situation. The first is to simply use a `goto` or a `gosub` as your statement.

The second is the `notif ... jumpf` pattern:
```
notif %myVar == 1 jumpf
  dwave 0,"myvoice.ogg"
  ^Hello!^@/
  dwave 0,"myothervoice.ogg"
  ^And goodbye!^@
~
```
So, for starters, `jumpf` will cause the code execution to transfer to the closest line containing a single `~` in it.

`notif` inverts the condition. That bit could be written as `if %myVar != 1 jumpf`, but using `notif` reads cleaner -- after all, we generally care about the condition when the code *does* execute, rather than when it does not.

Note that you *cannot* nest these, as the execution will always transfer to the *closest* `~`. This is not a block, and does not follow block-like logic.
If you want complex nested conditions, use the `goto` method.

# Loops
ponscripter supports for loops. They look like this:
```
for %myVar=1 to 10 step 1
  ^Hello!^@
next
```
The `step 1` part is optional, and specifies by how much the value is to be incremented/decremented. `1` is the default.

There are no `while` loops. However, you can use this construction to emulate them:
```
mov %myVar,0
~
if %myVar == 5 jumpf
  ; loop content here
  mov %myVar,%myVar+1
  jumpb
~
```
`jumpb` is kind of like `jumpf`, but scans the script for the `~` backwards.
So, we do our check, and if it's true we skip the loop body with `jumpf`. Inside the loop body, we `jumpb` back to the check so that we can re-do it.

If this is too confusing, you might want to just use `goto` with conditions instead.
# Conclusion
You now know how the ponscripter engine's basics work. Congratulations.

There is plenty this simplistic tutorial doesn't cover. But at this point, the [API docs](/ponscripter-fork/api ':ignore') should no longer be particularly terrifying to read, if you need to find out information about some other commands. You likely won't need that while working on mods, though knowing these basics should help you understand the rest if you do need it.
