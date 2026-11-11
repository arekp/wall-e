# Wall-E – wskazówki dla Claude Code

Projekt hobbystyczny: replika Wall-E (druk 3D), sterowana ESP32 z telefonu z Androidem. Odpowiadaj po polsku.

## Układ repozytorium

- `docs/electronics.md` – elektronika: założenia, piny, zasilanie, BOM. **Piny i kanały serw zmieniaj tylko tutaj i w `docs/wiring.svg`.**
- `docs/protocol.md` – protokół aplikacja ↔ ESP32. **Jedyne źródło prawdy**; firmware i aplikacja mają go implementować, nie wymyślać własnych komend.
- `android/` – aplikacja (Kotlin, Compose). `firmware/` – firmware ESP32 (planowany, PlatformIO/Arduino).
- `docs/wiring.svg|png` – schemat; generowany skryptem, po zmianie pinów trzeba go odtworzyć.

## Zasady pracy

1. Zmiana pinu, komendy lub zakresu serwa = zmiana w dokumentacji **w tym samym commicie** co w kodzie.
2. Nie zgaduj sprzętu: zakres serw, typ silników i akumulator są oznaczone ⚠️ w `docs/electronics.md`; dopóki nie są potwierdzone, trzymaj je w jednym miejscu (stałe/konfiguracja).
3. Bezpieczeństwo robota ma pierwszeństwo: fail-safe 500 ms na `drive`, limity kątów serw wymuszane w firmware, stop przy utracie połączenia i niskim napięciu baterii.
4. Mów wprost, co zostało zweryfikowane (zbudowane, uruchomione na sprzęcie), a co nie. Kod bez testu na robocie oznacz jako nieprzetestowany.
5. Małe, osobne commity; gałąź robocza zgodnie z poleceniem sesji; PR tylko na prośbę.

## Komendy

- Aplikacja: otwórz `android/` w Android Studio albo `cd android && ./gradlew assembleDebug` (wymaga Android SDK).
- Firmware (po dodaniu): `cd firmware && pio run`.
