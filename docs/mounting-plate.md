# Płytka montażowa elektroniki (druk 3D)

Status: **projekt nieprzetestowany** – siatki STL są poprawne geometrycznie (zamknięte, wygenerowane skryptem), ale płytka **nie była drukowana** ani przymierzana do prawdziwych modułów. Najpierw wydrukuj szablon (pkt 3).

**Wersja 3:** płyta ma 124,2 × 109,1 mm – limit zmierzony przez Ciebie we wnętrzu korpusu to **125 × 110 mm** (poprzednie wersje: 157 × 84 i 135 × 110 były za duże). Niesie **samą elektronikę** z faktycznymi wymiarami modułów: ESP32 NodeMCU v3 (31,6 × 58,2), **L298N** (43,7 × 43,7, zastąpił TB6612FNG) i **moduł zasilający 40 × 64** (zastąpił buck 6 V). **Akumulator leży osobno pod płytą.** Wszystkie moduły są obrócone tak, by zmieścić się w 125 × 110 mm; szczegóły i zapas – poniżej.

Pliki: [`hardware/mounting-plate/`](../hardware/mounting-plate/) – `plate.py` (generator, jedyne źródło wymiarów), `walle_plate.stl`, `walle_fit_coupon.stl`, `walle_spacer.stl`, `preview.png`.

![Podgląd](../hardware/mounting-plate/preview.png)

## 1. Koncepcja

Sprawdzone wzorce z internetu (płytki pod ESP32 na Printables/MakerWorld, porady o wkrętach i wkładkach gwintowanych) sprowadzają się do trzech rozwiązań: gwint w słupku, wkładka gwintowana albo wsuwana kieszeń. **Wybrałem kieszenie + opaski zaciskowe**, bo kilka z naszych modułów nie ma otworów montażowych (MAX98357A) lub mają je w nieznanych miejscach (klony PCA9685), a wymiary klonów się różnią.

- Każdy moduł leży w kieszeni wyznaczonej czterema narożnymi kątownikami (luz 0,3 mm na stronę) – bez śrub.
- Moduły są unieruchamiane opaską zaciskową 2,5–3,6 mm przez dwa otwory przy kieszeni (prowadzona w poprzek krótszego boku). Kątowniki sąsiednich kieszeni mogą się stykać (kieszenie są oddalone o ≥ 2 mm), ale nie wchodzą w cudzą kieszeń.
- Pod narożnikami płytek (poza ESP32) są półki 2,5 mm, żeby lutowania od spodu nie opierały się o płytę.
- **ESP32** ma kołki zlutowane w dół: pod obydwoma rzędami pinów jest szczelina przelotowa (obejmuje rozstaw rzędów 18–30 mm, więc także 22,86 / 25,4 / 27,94 – **rozstaw na Twojej płytce niezmierzony**). Dlatego płyta stoi na odstępnikach.
- Cztery otwory M3 w wolnych miejscach między kieszeniami, rozstawione możliwie daleko od siebie (skrypt wybiera je sam; nie są w narożnikach, bo tam stoją moduły); odstępniki ≥ 12 mm (kupne M3 żeńsko-żeńskie albo `walle_spacer.stl`, 4 szt.).
- Bez podpór: wszystkie ścianki pionowe, płyta płasko na stole. Płyta ma 124,2 × 109,1 mm i 3,5 mm grubości (do 10,5 mm z kątownikami).

Rozmieszczenie (słownik `POS` w `plate.py`, układ znaleziony wyszukiwaniem z obrotami modułów): lewa kolumna od dołu – **zasilacz serw 6 V**, nad nim **PCA9685** (krótkie grube przewody 6 V → V+), wyżej **buck 5 V**; prawa strona – u dołu **L298N** (blisko przewodów od akumulatora pod płytą), nad nim pionowo **ESP32**, a obok **MAX98357A**. Po prawej od ESP32 i u góry zostało wolne miejsce na bezpiecznik, wyłącznik i kondensatory.

**Akumulator pod płytą:** rozmiar jeszcze nieznany, więc płyta go nie obejmuje. Leży na dnie korpusu, a płyta stoi nad nim na odstępnikach. **Wysokość odstępnika = wysokość akumulatora + ok. 3 mm** (min. 12 mm ze względu na piny ESP32). Gdy zdecydujesz o baterii, wygeneruj odstępniki: `python3 hardware/mounting-plate/plate.py <wysokość_mm>` (np. `35`) albo kup odstępniki M3 żeńsko-żeńskie. Szerokość/głębokość akumulatora ogranicza tylko wnętrze korpusu, nie płyta.

**Poza płytką:** ESP32-CAM (osobny moduł w głowie/korpusie, ma tylko zasilanie i masę). Bezpiecznik, wyłącznik i kondensatory nie mają kieszeni – lutuj je w przewodzie przy modułach.

## 1a. Co gdzie leży

Numery jak na [podglądzie](../hardware/mounting-plate/preview.png). Współrzędne: lewy dolny róg kieszeni, od lewego dolnego rogu płyty, widok z góry [mm]. Piny i zasilanie – wg [`electronics.md`](electronics.md).

| Nr | Element | Pozycja kieszeni (x, y) i obrót | Co podłączyć / uwagi |
|---|---|---|---|
| 1 | ESP32 NodeMCU v3 | x 68, y 48,5 – prawa środkowa, pionowo (31,6 w poziomie × 58,2 w pionie) | USB skieruj ku górnej krawędzi płyty (kabel wyjdzie poza płytę – sprawdź miejsce w korpusie); antena bez metalu. Piny w dół przez dwie szczeliny |
| 2 | PCA9685 | x 2, y 48,5 – lewa środkowa, poziomo | I2C z ESP32; V+ serw z zasilacza 6 V (+1000 µF przy złączu) |
| 3 | Buck 5 V | x 2, y 76,5 – lewa góra, poziomo | Zasila ESP32, MAX98357A i logikę L298N (oraz ESP32-CAM poza płytą) |
| 4 | Zasilacz serw 6 V (40 × 64) | x 2, y 5,5 – lewy dolny róg, obrócony (64 w poziomie) | Tylko serwa; tuż pod PCA9685 |
| 5 | MAX98357A | x 103, y 48,5 – prawa środkowa | Głośnik, I2S z ESP32 |
| 6 | L298N | x 69, y 1,5 – prawy dół | Silniki gąsienic; VS wprost z baterii (+470 µF), ENA/ENB = PWM z ESP32. Ma radiator (ok. 25–30 mm wysokości) – **sprawdź wysokość w korpusie** |
| – | Akumulator 2S | **pod płytą**, na dnie korpusu | Rozmiar do ustalenia; bezpiecznik 5 A i wyłącznik w przewodzie „+” |

Zasada układu: ciężki akumulator nisko, pod płytą; zasilacz serw i PCA9685 jedno nad drugim; silnikowy L298N najbliżej akumulatora (zakłócenia od silników z dala od ESP32 i audio). Pozycje to wartości z `POS` w `plate.py`; po zmianie wymiarów modułów trzeba ułożyć je od nowa (skrypt sprawdza kolizje i limit 125 × 110 mm). Który bok płyty jest przodem robota, **jeszcze nie ustalono**.

### Napisy na płytce

W `walle_plate.stl` są **wyryte** (0,8 mm, litery 3,5 mm, czcionka pogrubiona) krótkie napisy, numerowane jak w tabeli: `1 ESP32`, `2 SERWA`, `3 5V` (buck 5 V), `4 6V` (zasilacz serw), `5 AMP` (MAX98357A), `6 MOTOR` (L298N). Napisy 1, 3 i 5 leżą **obok** swoich kieszeni, w wolnych miejscach; w gęstym układzie 125 × 110 mm nie ma obok pozostałych miejsca, więc napisy **2, 4 i 6 są wyryte na dnie kieszeni, pod modułem** – widać je tylko przed włożeniem modułu. Treść napisów to słownik `LABELS` w `plate.py`; podgląd rysuje dokładnie to, co jest w STL. **Nie sprawdzone w druku:** 0,8 mm głębokości i 3,5 mm liter powinno być czytelne przy dyszy 0,4 mm, ale nie wydrukowałem tego. Drukuj płytę licem do góry.

## 2. Wymiary i piny – co sprawdzono

Strony producentów (Adafruit, SparkFun, Espressif) były w tej sesji **niedostępne** (host poza dozwoloną siecią), więc wymiary pochodzą z wyników wyszukiwania i ofert sklepów. Rysunków mechanicznych nie widziałem.

| Moduł | Przyjęty PCB [mm] | Pewność | Uwagi i źródła |
|---|---|---|---|
| ESP32 NodeMCU v3 | 31,6 × 58,2 | **zmierzone przez Ciebie** | Rozstaw rzędów pinów niezmierzony – szczeliny pod piny obejmują 18–30 mm |
| PCA9685 | 62,5 × 25,4 | niska–średnia | Jedna oferta: [iFuturetech](https://ifuturetech.org/?p=512) (4 otwory ok. 2,5 mm); inna 60 × 25: [Sigmanortec](https://sigmanortec.ro/en/pca9685-module-i2c-interface-16-ch-servo-motor); 4 otwory ⌀3 mm wg [Tinytronics](https://www.tinytronics.nl/en/mechanics-and-actuators/motor-controllers-and-drivers/servomotor-controllers/16-channel-i2c-pwm-servo-controller-pca9685) (pozycji brak) |
| MAX98357A | 19,4 × 17,8 | średnia | [Adafruit 3006](https://www.adafruit.com/products/3006); klony mogą się różnić |
| Buck 5 V 3 A | 43 × 21 | ⚠️ założenie | Typowy LM2596 – **nie sprawdzano** |
| Zasilacz serw 6 V | 40 × 64 | **zmierzone przez Ciebie** | Moduł zasilający zamiast buck 6 V; typ/prąd wyjściowy nieznany ⚠️ |
| L298N | 43,7 × 43,7 | **zmierzone przez Ciebie** | Zastąpił TB6612FNG. Piny sterujące zmieniły nazwy (ENA/IN1–IN4/ENB), numery GPIO bez zmian; GPIO13 (STBY) wolny – patrz `electronics.md` |
| Akumulator 2S | – | nieznany | Poza płytą (leży pod nią) – patrz pkt 1 |

Nie zmieniałem pinów ani kanałów serw – piny pozostają wg [`electronics.md`](electronics.md). Płyta nie narzuca kolejności pinów; kable idą od spodu i górą przez otwory na opaski.

Nie ujęto: bezpiecznika, wyłącznika i kondensatorów (1000 µF, 470 µF – lutować przy złączach modułów).

## 3. Jak sprawdzić przed drukiem

1. Zmierz suwmiarką pozostałe moduły (buck 5 V, PCA9685, MAX98357A – nadal z ofert, nie z pomiaru; rozstaw rzędów pinów ESP32 i wysokość kołków) i popraw wartości w słowniku `MODULES` w `plate.py`. **Zapas jest minimalny** (kieszenie ≥ 2 mm od siebie, płyta 0,8 × 0,9 mm poniżej limitu), więc moduł większy o 1–2 mm może wymagać ponownego ułożenia `POS`.
2. Wydrukuj `walle_fit_coupon.stl` (ok. 5 cm³, kilkanaście minut): same obrysy kieszeni z kątownikami. Przyłóż moduły – mają wchodzić z lekkim oporem; jeśli luźno lub ciasno, zmień `CLEAR`.
3. Dopiero potem drukuj `walle_plate.stl`: PETG, warstwa 0,2 mm, 4 ścianki, brim (duża płaska płyta lubi się wyginać – **nie testowane**).
4. Odtworzenie plików po zmianach: `pip install trimesh manifold3d numpy matplotlib scipy networkx shapely mapbox_earcut && python3 hardware/mounting-plate/plate.py`.

## 4. Wnętrze korpusu – co wiadomo z modelu 3MF

Plik `RECOMENDED_WALL-E_Full_Pieces.3mf` (Printables, autor Kat; korpus skalowany ok. 41 % filmowego) zawiera **części rozłożone na stołach druku, nie złożony model**. Zmierzyłem z niego (siatki, nie rysunki):

| Część | Wymiar [mm] | Wniosek |
|---|---|---|
| `body-bottom` | 156 × 161,3 × 5 | obrys dna; dno ma od spodu zagłębienie 136 × 123,6 |
| `body-front`, `body-back` | 141,9 szer. × 143 wys. | wchodzą między ściany boczne → **wnętrze ok. 142 mm szer.** |
| `body-left/right` | 162,7 × 143, płyta ok. 6–8 mm | wysokość korpusu ok. 143 mm |
| `body-front-door` | 120,5 × 84,4 | klapa/okno w froncie – tędy nie włożysz płyty, montuj przed zamknięciem korpusu |

**Limit płyty:** to, co wyszło z 3MF (wnętrze ok. 142 × ≥124 mm), okazało się za optymistyczne – po zbudowaniu korpusu **zmierzyłeś realny limit 125 × 110 mm** i to on obowiązuje (`PLATE_MAX_W`, `PLATE_MAX_D` w `plate.py`). Skrypt przerywa, gdy płyta go przekracza. Zapas to 0,8 × 0,9 mm.

**Niezweryfikowane (zrób przymiarkę na wydrukowanych częściach korpusu):**
- **Wysokość i kolizje**: nie sprawdzono zestawu z serwami szyi/ramion, silnikami ani żebrami w ścianach bocznych. Stos: odstępnik (≥ 12, w praktyce wysokość akumulatora + 3) + płyta 3,5 + kątowniki ≈ 7 + moduły, plus wysokość modułów (kondensatory bucków, ESP32 ok. 15–20 mm).
- **Sposób mocowania**: dno ma własne otwory (m.in. 4 × ⌀3 w rozstawie 100 × 58 mm, 4 × ⌀3 w rozstawie 40 × 17 mm), ale **nie wiem, do czego służą** (silniki? zaczepy?), więc otwory M3 płyty nie są z nimi zgrane. Płytę możesz przykręcić przez odstępniki do wywierconych otworów, przykleić do dna na gorący klej lub zgrać otwory po przymiarce – otwory M3 skrypt wybiera automatycznie w wolnych miejscach (`hole_positions`).
- Dopasowanie do korpusu to tylko porównanie wymiarów, nie symulacja złożenia.
