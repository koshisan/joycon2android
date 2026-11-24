# joyhands output

This fork's addition: with **Send to joyhands** on (Settings), every report of a left or right
Joy-Con also goes to [joyhands](https://github.com/koshisan/joyhands)' sender on the same device.
joyhands uses Joy-Cons as buttons and sticks for hand-tracked PC VR; its sender, `jcstream`, forwards
them to a SteamVR driver. It reads Joy-Con 1 from the kernel itself, but Joy-Con 2 have no kernel
driver, so they come from this app.

- **Independent of the output card.** It needs no player assignment, no Shizuku and no virtual
  device, and runs beside the other outputs. Leave the virtual gamepad off while streaming PC VR, or
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
- Nothing listens when joyhands' sender is not running; the datagrams are then dropped.
