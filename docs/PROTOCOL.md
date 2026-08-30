# MPad wire protocol v1

MPad uses the same little-endian frame format on LAN and Bluetooth RFCOMM. LAN sends discovery and gamepad state over UDP; pairing, authentication, keepalive, errors, and rumble use TCP. Bluetooth carries every frame over one RFCOMM byte stream.

## Frame

| Offset | Size | Field |
| ---: | ---: | --- |
| 0 | 4 | ASCII `MPAD` |
| 4 | 1 | Protocol version (`1`) |
| 5 | 1 | Message type |
| 6 | 2 | Flags; bit 0 means authenticated |
| 8 | 2 | Payload byte length |
| 10 | 2 | Reserved, zero |
| 12 | 4 | Session ID |
| 16 | 4 | Sequence number |
| 20 | N | Payload |
| 20+N | 16 | Optional HMAC-SHA256 prefix |

The authentication tag covers the header and payload. Discovery, pairing, and the initial authentication response are unauthenticated; session traffic is authenticated with the 256-bit pairing token.

Message types are discovery request/response (`1/2`), pair request/response (`3/4`), authentication request/response (`5/6`), input (`10`), rumble (`11`), ping/pong (`12/13`), disconnect (`14`), and error (`15`). Pairing and discovery payloads are UTF-8 JSON.

## Input payload

The 24-byte input payload contains: monotonic microseconds (`Int64`), buttons (`UInt32`), hat (`UInt8`, `0..7`, neutral `8`), LX/LY/RX/RY (`Int16`), LT/RT (`UInt8`), and battery percent (`UInt8`, unknown `255`). Every input packet is a complete state snapshot. Receivers discard old sequence numbers and neutralize all controls after 250 ms without a valid state.

Button bits are A, B, X, Y, LB, RB, Back, Start, Guide, L3, and R3 at bits `0..10`. Positive stick Y points upward.

## Rumble payload

The 8-byte rumble payload contains low-frequency motor (`UInt8`), high-frequency motor (`UInt8`), duration in milliseconds (`UInt16`), and event ID (`UInt32`).

