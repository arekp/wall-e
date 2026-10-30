# Protokół sterowania Wall-E (aplikacja ↔ główny ESP32)

Ten dokument jest jedynym źródłem prawdy dla komunikacji. Używa go aplikacja na Androida
(`android/`) i będzie go implementował firmware głównego ESP32.

## Transport

- Sieć: Wi-Fi AP `WALLE` wystawiany przez główny ESP32 (`192.168.4.1`).
- Połączenie: WebSocket `ws://192.168.4.1/ws`.
- **Ramki tekstowe** = komendy i statusy w formacie JSON (jedna wiadomość = jedna komenda).
- **Ramki binarne** = dźwięk z mikrofonu telefonu (patrz niżej).
- Wideo nie idzie tym kanałem: ESP32-CAM serwuje MJPEG pod `http://192.168.4.10:81/stream`.

## Komendy (aplikacja → robot, tekst JSON)

| Komenda | Przykład | Działanie |
|---|---|---|
| Jazda | `{"c":"drive","x":0.0,"y":0.8}` | `y` przód/tył, `x` skręt, zakres −1..1; robot miesza na lewy/prawy silnik. Wysyłana ok. 20 Hz, dopóki użytkownik trzyma joystick |
| Stop | `{"c":"stop"}` | natychmiast zatrzymuje silniki |
| Serwo | `{"c":"servo","id":"armL","a":45}` | ustawia serwo na kąt (°); robot ogranicza kąt do zakresu z `electronics.md` |
| Poza | `{"c":"pose","n":"hello"}` | sekwencja serw: `home`, `hello`, `sad`, `curious` |
| Dźwięk zapisany | `{"c":"sound","n":"walle"}` | odtwarza próbkę zapisaną w pamięci ESP32 (np. `walle`, `eve`, `beep`) |
| Mikrofon | `{"c":"audio","on":true}` | włącza/wyłącza odbiór dźwięku z telefonu (patrz niżej) |
| Głośność | `{"c":"vol","v":70}` | 0–100 |
| Ekran | `{"c":"oled","t":"EVE?"}` | tekst na OLED (jeśli jest zamontowany) |
| Status | `{"c":"status"}` | prosi o status |

Identyfikatory serw (`id`): `neckLow`(0), `neckUp`(1), `head`(2), `armL`(3), `armR`(4), `eyeL`(5), `eyeR`(6), `handL`(7), `handR`(8).
Numer w nawiasie to kanał PCA9685. Robot przyjmuje też sam numer kanału (`"id":3`).

## Statusy (robot → aplikacja, tekst JSON)

```json
{"t":"status","bat":7.84,"cam":true,"audio":false}
```

- `bat` – napięcie akumulatora w woltach (pomiar na GPIO34),
- `cam` – czy ESP32-CAM jest podłączona do sieci (opcjonalnie, jeśli główny ESP32 ją wykrywa),
- `audio` – czy robot odbiera dźwięk.

Robot wysyła status po komendzie `status` i może też sam co kilka sekund.

## Dźwięk z telefonu przez głośnik Wall-E

Cel: użytkownik mówi do telefonu, a głos wydaje głośnik w robocie (MAX98357A + głośnik 3 W, na I²S).

1. Aplikacja wysyła `{"c":"audio","on":true}` i zaczyna wysyłać ramki **binarne**.
2. Format ramki: surowe PCM, **16 bitów ze znakiem, little-endian, mono, 16 000 Hz**.
   Zalecany rozmiar: 20 ms = 320 próbek = **640 bajtów** (dowolny rozmiar parzysty jest dopuszczalny).
3. Robot kolejkuje próbki w buforze kołowym (ok. 100–150 ms, żeby wygładzić jitter Wi-Fi) i odtwarza je przez I²S.
   Gdy bufor się opróżni, odtwarza ciszę; gdy się przepełni, porzuca najstarsze próbki.
4. Aplikacja wysyła `{"c":"audio","on":false}` po zwolnieniu przycisku „mów”. Robot wycisza wzmacniacz.
5. Opóźnienie całej ścieżki: ok. 150–300 ms. Przepływ: 256 kb/s (nieskompresowane), co Wi-Fi AP ESP32 obsługuje bez problemu.

Uwagi praktyczne:
- Gdy telefon leży przy robocie, mikrofon słyszy głośnik robota (sprzężenie, pisk). Aplikacja działa w trybie „push-to-talk” i używa źródła `VOICE_COMMUNICATION` z wbudowaną eliminacją echa, ale najlepiej mówić z dala od robota.
- Strumień wideo i audio dzielą jeden kanał Wi-Fi; przy opóźnieniach zmniejsz rozdzielczość kamery.

## Bezpieczeństwo

- Brak komendy `drive` przez **500 ms** zatrzymuje silniki (robot, niezależnie od aplikacji).
- Utrata połączenia WebSocket zatrzymuje silniki i wyłącza odbiór dźwięku.
- Aplikacja przy zwolnieniu joysticka wysyła jawne `stop`.
