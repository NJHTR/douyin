# Sound + Haptic Design System

This product uses semantic interaction events. Feature code must call
`playSound('message.received')` (or a higher-level interaction adapter), never
construct an `Audio` object or reference a sound filename directly.

## Principles

- Sound, haptic and visual feedback describe the same state; sound is never the only signal.
- A cue is informative, short and interruptible unless explicitly marked as a call loop.
- Current-chat messages are silent. Group bursts are deduplicated and aggregated by event id.
- Route changes, backgrounding, page hide and call termination stop transient audio immediately.
- Browser haptics are best-effort. Native clients should map the same event to platform-standard haptics.

## Tokens

The canonical token set lives in `src/utils/notificationFeedback.ts` as `SoundEvent`:

`ui.*`, `navigation.*`, `sheet.*`, `message.*`, `like`, `follow`, `favorite`,
`upload.*`, `call.*`, and `notification.*`.

The token is the stable contract. The selected sound pack can change without
changing business code. Current fallback synthesis is intentionally quiet and
temporary; licensed CC0/CC-BY assets may be mapped in a future pack manifest.

## Priority and volume

| Priority | Meaning | Examples |
| --- | --- | --- |
| P0 | Critical attention | `call.incoming`, `call.ringing` |
| P1 | Direct attention | direct message, call state |
| P2 | Important system | mention, error, upload failure |
| P3 | Normal notification | normal notification, success |
| P4 | Engagement | like, follow, favorite |
| P5 | Micro feedback | tap, navigation, progress |

Policy volume is relative to the user setting. Modules must not pass ad-hoc
`0.8`, `0.9` or `1.0` values. Critical calls may loop; all other cues are
short and cacheable.

## Policy fields

Each token defines `sound`, `haptic`, `priority`, `volume`, `cooldownMs`,
`interruptible` and `dedupe`. `eventId` prevents WebSocket and notification
service duplicates. The default cooldown is 120-1500ms depending on priority.

## Call policy

`call.incoming` / `call.ringing` start a ringtone loop plus a periodic haptic.
`call.connecting` is a short transition cue. `call.connected` is a short
confirmation. `call.reconnecting` is throttled. `call.rejected`, `call.busy`,
`call.timeout`, `call.failed` and `call.ended` stop the loop before playing a
single end cue. The RTC store owns this lifecycle, so UI unmounts cannot leave
ringing audio behind.

## Accessibility and lifecycle

Settings include global enable, sound enable, volume, vibration enable and
reduced feedback. Every important state also has visual UI. Autoplay unlock is
requested on the first user gesture. Audio is paused/stopped on page hide and
background transitions; ringtone is never allowed to leak after route change,
call end or modal close.

## Resource and licensing record

| Source | License | Local mapping | Purpose |
| --- | --- | --- | --- |
| Current fallback synthesis | Project code | `notificationFeedback.ts` | Development fallback |
| UI SFX (`romainsimon/uisfx`) | CC0 audio, MIT runtime (verify upstream before vendoring) | Not vendored yet | Candidate semantic pack |
| SND (`snd-lib/snd-lib`) | Verify individual asset metadata before use | Not vendored yet | Candidate alternate pack |

No unknown-license audio is copied into this repository. Any future asset must
record source, license, file hash/path and token mapping in this table before
shipping.

## Verification checklist

Test at least 30 interactions across foreground/background, route changes,
network loss, call accept/reject/timeout, duplicate message delivery and group
bursts. Verify visual state remains understandable with sound and haptic muted.
