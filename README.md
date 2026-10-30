# Wall-E

Hobbystyczna replika robota Wall-E: wydrukowana w 3D, z napędem gąsienicowym, serwami w szyi, ramionach i oczach,
kamerą w oku i sterowaniem z telefonu przez Wi-Fi. Projekt bazuje na innych projektach z internetu (linki niżej).

## Status

| Obszar | Stan |
|---|---|
| Mechanika (druk 3D) | wydrukowana |
| Silniki i serwa | dostępne |
| Elektronika | **zaprojektowana, jeszcze niezmontowana** – założenia do potwierdzenia, patrz [`docs/electronics.md`](docs/electronics.md) |
| Protokół komend i dźwięku | opisany: [`docs/protocol.md`](docs/protocol.md) |
| Aplikacja na Androida | napisana, **jeszcze nie zbudowana ani nie testowana** – patrz [`android/`](android/) |
| Firmware ESP32 (główny) | planowany |
| Firmware ESP32-CAM | planowany |

## Zawartość repozytorium

| Plik | Opis |
|---|---|
| [`WALL-E.3mf`](WALL-E.3mf) | projekt do druku (14 płyt): korpus, gąsienice i przekładnie, szyja, głowa z oczami, ramiona, dłonie, ramka OLED, mocowanie kamery, modyfikacje „solar” |
| [`docs/electronics.md`](docs/electronics.md) | dokumentacja elektroniki: założenia, zasilanie, mapa pinów, kanały serw, protokół komend, BOM, otwarte pytania |
| [`docs/protocol.md`](docs/protocol.md) | protokół WebSocket: komendy JSON, statusy, format ramek audio (głos z telefonu) |
| [`android/`](android/) | aplikacja Android (Kotlin, Jetpack Compose): joystick, serwa, pozy, dźwięki, podgląd z kamery, „przytrzymaj i mów” |
| [`docs/wiring.svg`](docs/wiring.svg), [`docs/wiring.png`](docs/wiring.png) | schemat połączeń |

## Architektura w skrócie

```
 Telefon (przeglądarka) ──Wi-Fi „WALLE”──► Główny ESP32 (punkt dostępu 192.168.4.1)
        │                                    ├─ TB6612FNG → 2 silniki gąsienic
        │                                    ├─ PCA9685  → serwa (szyja, głowa, ramiona, oczy)
        │                                    ├─ OLED (opcja), MAX98357A (opcja)
        └──── obraz MJPEG ◄──────────────── ESP32-CAM (klient Wi-Fi, 192.168.4.10)
```

- Jeden punkt dostępu (`WALLE`) wystawia główny ESP32; ESP32-CAM i telefon są jego klientami.
- Sterowanie: aplikacja na Androida (joystick, suwaki serw, pozy, dźwięki, podgląd z kamery).
- Głos: mówisz do telefonu (przycisk „przytrzymaj i mów”), dźwięk wydaje głośnik w robocie (MAX98357A).
- Komendy to wiadomości JSON przez WebSocket, głos to ramki binarne PCM; po utracie połączenia silniki zatrzymują się po 500 ms.
- Zasilanie: akumulator 2S Li-ion 7,4 V, osobne przetwornice dla logiki (5 V) i serw (6 V), silniki zasilane bezpośrednio.

Szczegóły, piny i protokół: [`docs/electronics.md`](docs/electronics.md).

## Plan prac

1. Potwierdzić założenia sprzętowe (typ silników, liczba serw, akumulator) – sekcja „Otwarte pytania” w dokumentacji elektroniki.
2. Złożyć elektronikę wg schematu i sprawdzić silniki oraz serwa pojedynczo.
3. Napisać firmware głównego ESP32 (AP, WebSocket, interpreter komend, fail-safe).
4. Skonfigurować ESP32-CAM (klient AP, stały adres, strumień MJPEG).
5. Zbudować i przetestować aplikację Androida z prawdziwym robotem; skalibrować zakresy serw na gotowym modelu.

## Linki i źródła

Projekt powstał na podstawie poniższych prac; przed publikacją własnych zmian sprawdź warunki licencji oryginałów.

- https://www.youtube.com/watch?v=ZRK9f4FZlD8
- https://wired.chillibasket.com/3d-printed-wall-e/
- https://makerworld.com/en/models/620832-higher-quality-wall-e-model?from=search#profileId-1951286
- https://www.thingiverse.com/thing:3703555
- https://www.printables.com/model/408363-wall-e
- https://github.com/chillibasket/walle-replica
