# Kipat backdrop probe

A throwaway rig that answers one question before you build anything else:

> When an NFC tap launches a translucent Activity, what does Android actually
> composite behind it on this handset?

Spec section 13 assumes the answer is "the previous app, live." That is true on
some devices and not on others, and the whole premium-overlay positioning rests
on it. Find out in an afternoon rather than in month three.

## What is in here

```
app/build.gradle.kts
app/src/main/AndroidManifest.xml          the attributes that make or break it
app/src/main/res/values/themes.xml        Theme.Kipat.Tap, commented line by line
app/src/main/java/com/kipat/probe/
  TapExperienceActivity.kt                the activity under test
  MainActivity.kt                         dev harness: tag writer + verdict log
```

Drop the `app` folder into a new empty Android Studio project (Kotlin, Compose,
no template) and build the debug variant.

## Running a test

1. Install on the device.
2. Open the harness, press **Arm tag write**, hold a blank NTAG 213/215/216
   against the phone. It writes a URL record plus an Android Application Record.
3. Close the app fully. Open something with a busy, recognisable screen —
   Instagram, YouTube, a photo. A moving video is the best test subject because
   it makes "live" versus "frozen" obvious at a glance.
4. Tap the tag.
5. The probe opens with a small panel at the bottom and nothing else. Look at
   the rest of the screen and press the verdict:

   | Verdict | Means |
   |---|---|
   | LIVE | the prior app, still animating. Section 13 works as written. |
   | FROZEN | a static snapshot of the prior app. Good enough visually; rules out backdrop effects that assume live content. |
   | BLACK | nothing behind. The overlay illusion does not exist on this device. |
   | HOME | launcher or wallpaper instead of the prior app. |

6. Cycle the four backdrop modes (TRAN / SCRI / BLUR / WALL) and record a
   verdict for each. Repeat the whole run per device.
7. Verdicts accumulate in the harness screen and in
   `/data/data/com.kipat.probe/files/probe-log.txt`.

No tag handy: `adb shell am start -a android.intent.action.VIEW -d "kipatprobe://tap/TEST123"`.
That reaches the same Activity but launches from the shell's task, so treat it
as a smoke test only — it does not answer the backdrop question.

## Device matrix

Test on at least one of each. Skins diverge more than Android versions do, and
these are the ones that matter for a Philippine launch.

- Pixel, current Android — the reference behaviour
- Samsung One UI — largest installed base, most aggressive window management
- Xiaomi HyperOS / MIUI — known for altering activity transitions
- Transsion (Tecno / Infinix) or realme, Android 11–13, entry tier — where the
  first-frame budget will actually hurt
- One device on API 26–28 — no `setTranslucent`, no frame-commit callback

## Reading the results

- **LIVE or FROZEN across the matrix** — build section 13 as specified.
- **Mixed** — you need a per-device capability check at runtime and a designed
  fallback for the black case. The fallback should be a deliberate look (a solid
  branded canvas that the reveal is designed for), never an accidental black
  screen. Decide this now; retrofitting it into the animation templates later is
  expensive.
- **BLACK on a device you care about** — the "overlay on your current app"
  language has to come out of the positioning for that segment, and the reveal
  becomes a fast full-screen moment instead. Still a good product; different
  marketing promise.

Also watch the **first frame** number in the panel. The spec budgets 0–100 ms
for launch. A cold start with an empty Compose tree is realistically 120–400 ms
on mid-range hardware. If you cannot get under roughly 250 ms, the first haptic
at 150 ms has nothing to accompany it and the sequence in section 12 needs
rebasing on measured numbers.

## Things this rig deliberately does not do

- No animation. Anything drawn is something you cannot see the backdrop
  through, and the point is to see the backdrop.
- No network, no Supabase, no verification. Section 97 says prove the visual
  product first.
- No `SYSTEM_ALERT_WINDOW`. The spec rules it out and it would mask the exact
  behaviour being measured.

## Caveats worth knowing before you start

- NFC tag dispatch requires the screen on and the device unlocked. There is no
  lock-screen tap path for this.
- `FLAG_BLUR_BEHIND` is API 31+ and can be off at the system level even there.
  The code falls back to a dim and logs it; do not read a fallback as a blur.
- `FLAG_SHOW_WALLPAPER` shows the wallpaper, not the prior app. It is in the
  mode list only so you can see for yourself that it is not the fix for BLACK.
- Delete the `kipatprobe://` intent filter before any of this code goes near a
  real build.
