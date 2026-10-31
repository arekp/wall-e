# Aplikacja Android – sterowanie Wall-E

Kotlin + Jetpack Compose. Łączy się z siecią Wi-Fi `WALLE` robota i steruje nim przez WebSocket
(protokół: [`../docs/protocol.md`](../docs/protocol.md)).

> **Status: kod napisany, ale jeszcze niezbudowany ani nieprzetestowany** (brak Android SDK w środowisku, w którym powstał).
> Pierwszy krok to otwarcie projektu w Android Studio i sprawdzenie, czy się kompiluje.

## Funkcje

- **Połącz:** łączy się z siecią `WALLE` (system pokaże prośbę o zgodę) i wiąże ruch aplikacji z tą siecią, więc Android nie przełącza się na dane komórkowe.
- **Jazda:** joystick (20 komend/s), przycisk STOP; zatrzymanie po puszczeniu joysticka i po zejściu aplikacji w tło.
- **Serwa:** suwaki dla 9 serw, zakresy jak w `docs/electronics.md`.
- **Pozy i dźwięki:** przyciski wywołujące gotowe sekwencje i próbki zapisane w ESP32.
- **Głos:** „przytrzymaj i mów” – mikrofon telefonu → ramki PCM 16 kHz mono → głośnik w robocie.
- **Kamera:** podgląd MJPEG z ESP32-CAM (`http://192.168.4.10:81/stream`).
- **Status:** napięcie akumulatora, stan połączenia.

## Wygląd

Motyw z bajki: żółta blacha, rdza i piaskowe tło, czcionka monospace. Logo (oczy-lornetka Wall-E, własny rysunek wektorowy
`res/drawable/ic_walle_logo.xml`) jest ikoną aplikacji i widnieje w nagłówku. Paleta: `ui/Theme.kt`.

## Instalacja na telefonie i aktualizacje

Każdy build na GitHub Actions publikuje wydanie z podpisanym APK. Stały adres do pobrania najnowszej wersji:

`https://github.com/arekp/wall-e/releases/latest/download/walle.apk`

1. Otwórz ten adres w przeglądarce telefonu i pobierz `walle.apk`; zezwól na instalację z tego źródła.
2. Kolejne wersje instalują się **na wierzch** (ten sam klucz podpisu, rosnący numer wersji), bez utraty ustawień.
3. Aktualizacja: w aplikacji Ustawienia → „Aktualizacja” otwiera stronę najnowszego wydania. Automatycznie aktualizuje aplikacja Obtainium (dodaj w niej `https://github.com/arekp/wall-e`).

Klucz podpisu (`app/walle.keystore`) jest hobbystyczny i publiczny w repo – wystarcza do instalacji poza sklepem, ale nie do publikacji w Google Play.

## Tryb demo (bez robota)

Przycisk **Demo** w aplikacji symuluje robota wewnątrz telefonu: nie wymaga Wi-Fi ani sprzętu. Komendy (jazda, serwa,
pozy, dźwięki, głośność) i ilość dźwięku z mikrofonu widać w dzienniku na ekranie. Pozwala sprawdzić interfejs i mikrofon.

## Budowanie

1. Zainstaluj Android Studio (Ladybug lub nowsze) i JDK 17.
2. Otwórz katalog `android/`; Android Studio pobierze Gradle i zależności.
3. Podłącz telefon (Android 10+) lub uruchom emulator – **do testu z robotem potrzebny jest prawdziwy telefon z Wi-Fi**.
4. W aplikacji: Ustawienia → wpisz nazwę sieci i hasło Wi-Fi robota (to samo, co w firmware) → Połącz.

## Struktura

| Plik | Rola |
|---|---|
| `WalleClient.kt` | WebSocket (OkHttp), budowanie komend JSON, odbiór statusów |
| `AudioStreamer.kt` | nagrywanie mikrofonu i wysyłanie ramek po 20 ms |
| `WifiConnector.kt` | połączenie z siecią robota i wiązanie ruchu aplikacji do niej |
| `WalleViewModel.kt` | cała logika: jazda, serwa, głos, ponawianie połączenia, ustawienia |
| `ui/Screens.kt` | ekrany Compose: joystick, serwa, przycisk mówienia, podgląd kamery |

## Znane ograniczenia

- Przy mówieniu blisko robota mikrofon słyszy głośnik (sprzężenie); używane jest źródło `VOICE_COMMUNICATION` z eliminacją echa, ale najlepiej mówić z daleka.
- Podgląd kamery to `WebView` z tagiem `<img>`; wystarczy do sterowania, ale nie pozwala na nagrywanie.
- Hasło Wi-Fi jest zapisane w `SharedPreferences` bez szyfrowania.
