# joyhands output

This fork's addition: with **Send to joyhands** on (Settings), every report of a left or right
Joy-Con also goes to [joyhands](https://github.com/koshisan/joyhands)' sender on the same device.
joyhands uses Joy-Cons as buttons and sticks for hand-tracked PC VR; its sender, `jcstream`, forwards
them to a SteamVR driver. It reads Joy-Con 1 from the kernel itself, but Joy-Con 2 have no kernel
driver, so they come from this app.

- **Independent of the output card.** It needs no player assignment and no virtual device, and
  runs beside the other outputs. Shizuku is only needed for Joy-Con 1: [the sender](#the-sender). Leave the virtual gamepad off while streaming PC VR, or
  the streaming app sees a gamepad too.
- **One datagram per report**, to `127.0.0.1:27817`, 41 bytes little-endian (`JoyhandsPacket`,
  `struct jh_ble` in joyhands' `puck/jcstream.c`):

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | magic `JHB1` (`0x3142484A`) |
| 4 | u8 | protocol, 1 |
| 5 | u8 | side: 0 left, 1 right |
| 6 | u8 × 6 | Bluetooth address |
| 12 | u16 | buttons, joyhands' bits |
| 14 | i16 × 2 | stick x, y: −32767..32767, right and down positive |
| 18 | i16 × 3 | accelerometer, 4096 per g |
| 24 | i32 × 3 | gyroscope, thousandths of a degree per second |
| 36 | u8 | battery in percent, 255 unknown |
| 37 | u8 | 1 if the accent colour follows |
| 38 | u8 × 3 | accent colour, red green blue |

- **Rate.** The connection interval is the report rate. The setting counts as a running output for
  **Faster controller updates**, so the Joy-Cons report every 15 ms while it is on.
- Sticks go out as `StickCalibrator` leaves them; buttons are the raw bitmap, before any mapping.

## The sender

`jcstream` is part of this app (`app/src/main/cpp/jcstream.c`, packaged as `libjcstream.so`), a copy of
`puck/jcstream.c` in koshisan/joyhands: change it there and copy it over. `JoyhandsSender` runs it
while the setting is on.

| Shizuku | Runs as | Reaches |
|---|---|---|
| running, permission given | the shell user, from `/data/local/tmp/jcstream` | Joy-Con 2, and Joy-Con 1 through the kernel's input devices |
| otherwise | this app | Joy-Con 2 only |

- **One sender at a time.** `jcstream` leaves at once when its port `27817` is taken. Started as
  the shell user it first ends any other, by exact name: an older copy of its own, or the one the
  joyhands app started.
- It restarts when Shizuku comes or goes, and ends with this app.

## Vibration

The other way round: the SteamVR driver of joyhands sends every vibration SteamVR asks a hand for to
the sender, which passes it on to `127.0.0.1:27818` while the setting is on. 14 bytes little-endian
(`JoyhandsHapticPacket`, `struct jh_haptic`):

| Offset | Type | Field |
|---|---|---|
| 0 | u32 | magic `JHV1` (`0x3156484A`) |
| 4 | u8 | protocol, 1 |
| 5 | u8 | side: 0 left, 1 right |
| 6 | u16 | duration in ms, 0 for a single pulse |
| 8 | u16 | frequency in Hz |
| 10 | u16 | amplitude, 0..65535 |
| 12 | u16 | zero |

- `JoyhandsVibration` plays it as [HD rumble](protocol.md#hd-rumble) on the Joy-Con of that side,
  assigned to a player or not, and stops it when its time is up.
- **Under 200 Hz is the strong motor, anything else the weak one**, at half the asked amplitude.
  The split and the strength are guesses, not yet tuned on hardware.
- **At least 40 ms.** A single pulse from SteamVR is shorter than the 12 ms one rumble packet carries.
