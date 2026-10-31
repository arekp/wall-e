# Płytka montażowa elektroniki (druk 3D)

Status: **projekt nieprzetestowany** – siatki STL są poprawne geometrycznie (zamknięte, wygenerowane skryptem), ale płytka **nie była drukowana** ani przymierzana do prawdziwych modułów. Najpierw wydrukuj szablon (pkt 3).

Pliki: [`hardware/mounting-plate/`](../hardware/mounting-plate/) – `plate.py` (generator, jedyne źródło wymiarów), `walle_plate.stl`, `walle_fit_coupon.stl`, `walle_spacer.stl`, `preview.png`.

![Podgląd](../hardware/mounting-plate/preview.png)

## 1. Koncepcja

Sprawdzone wzorce z internetu (płytki pod ESP32 na Printables/MakerWorld, porady o wkrętach i wkładkach gwintowanych) sprowadzają się do trzech rozwiązań: gwint w słupku, wkładka gwintowana albo wsuwana kieszeń. **Wybrałem kieszenie + opaski zaciskowe**, bo kilka z naszych modułów nie ma otworów montażowych (SparkFun TB6612, MAX98357A, ESP32-CAM) lub mają je w nieznanych miejscach (klony PCA9685), a wymiary klonów się różnią.

- Każdy moduł leży w kieszeni wyznaczonej czterema narożnymi kątownikami (luz 0,3 mm na stronę) – bez śrub.
- Moduły są unieruchamiane opaską zaciskową 2,5–3,6 mm przez dwa otwory przy kieszeni (prowadzona w poprzek krótszego boku).
- Pod narożnikami płytek (poza ESP32) są półki 2,5 mm, żeby lutowania od spodu nie opierały się o płytę.
- **ESP32** ma kołki zlutowane w dół: pod obydwoma rzędami pinów jest szczelina przelotowa (obejmuje rozstaw 22,86 i 25,4 mm). Dlatego płyta stoi na odstępnikach.
- Cztery otwory M3 w narożnikach; odstępniki ≥ 12 mm (kupne M3 żeńsko-żeńskie albo `walle_spacer.stl`, 4 szt.).
- Bez podpór: wszystkie ścianki pionowe, płyta płasko na stole. Płyta ma 150,7 × 135,3 mm i 3,5 mm grubości (do 10,5 mm z kątownikami).

Rozmieszczenie (od lewej-dołu): ESP32, PCA9685 · TB6612FNG, MAX98357A, ESP32-CAM · buck 5 V, buck 6 V.

## 2. Wymiary i piny – co sprawdzono

Strony producentów (Adafruit, SparkFun, Espressif) były w tej sesji **niedostępne** (host poza dozwoloną siecią), więc wymiary pochodzą z wyników wyszukiwania i ofert sklepów. Rysunków mechanicznych nie widziałem.

| Moduł | Przyjęty PCB [mm] | Pewność | Uwagi i źródła |
|---|---|---|---|
| ESP32 DevKit 38 pin | 55 × 28 | średnia | Oferty podają 51–55 × 25–28. Rozstaw rzędów pinów: 25,4 (oryginalny DevKitC V4) lub 22,86 (większość klonów) – [wątek esp32.com](https://esp32.com/viewtopic.php?p=89735), [espboards.dev](https://www.espboards.dev/esp32/esp32-38pin-devkit-generic/), [Cirkit Designer](https://docs.cirkitdesigner.com/component/9d3cdee3-1229-4034-848b-eed5092b800f/esp32-38-pin) |
| PCA9685 | 62,5 × 25,4 | niska–średnia | Jedna oferta: [iFuturetech](https://ifuturetech.org/?p=512) (4 otwory ok. 2,5 mm); inna 60 × 25: [Sigmanortec](https://sigmanortec.ro/en/pca9685-module-i2c-interface-16-ch-servo-motor); 4 otwory ⌀3 mm wg [Tinytronics](https://www.tinytronics.nl/en/mechanics-and-actuators/motor-controllers-and-drivers/servomotor-controllers/16-channel-i2c-pwm-servo-controller-pca9685) (pozycji brak) |
| TB6612FNG | 25,4 × 22,9 | niska | Klony: 24 × 21, 25,4 × 22,9 ([KAmod](https://wiki.kamamilabs.com/extensions/JZPDFGen/pdf/KAmod%20Motor%20Driver%20TB6612FNG-2329.pdf)); SparkFun ok. 20,3 × 20,3 ([sklep](https://www.sparkfun.com/sparkfun-motor-driver-dual-tb6612fng-1a.html)), bez otworów. Wzięty największy wymiar |
| MAX98357A | 19,4 × 17,8 | średnia | [Adafruit 3006](https://www.adafruit.com/products/3006); klony mogą się różnić |
| ESP32-CAM AI-Thinker | 27 × 40,5 | wysoka (kontur) | [CNX Software](https://www.cnx-software.com/2018/09/30/esp32-cam-esp32-camera-board/), brak otworów ([Raspberry Pi Magazine](https://magazine.raspberrypi.com/articles/esp32-cam-review)) |
| Buck 5 V 3 A | 43 × 21 | ⚠️ założenie | Typowy LM2596 – **nie sprawdzano** |
| Buck 6 V 5 A | 51 × 23 | ⚠️ założenie | Typowy XL4015 – **nie sprawdzano** |

Nie zmieniałem pinów ani kanałów serw – piny pozostają wg [`electronics.md`](electronics.md). Płyta nie narzuca kolejności pinów; kable idą od spodu i górą przez otwory na opaski.

Nie ujęto: akumulatora 2S, bezpiecznika, wyłącznika, kondensatorów (1000 µF, 470 µF – lutować przy złączach modułów) ani samego korpusu robota. **Wnętrze korpusu modelu z Printables nie zostało zmierzone**, więc nie wiadomo, czy płyta 151 × 135 mm się w nim zmieści – to pierwsze, co trzeba sprawdzić.

## 3. Jak sprawdzić przed drukiem

1. Zmierz suwmiarką wszystkie posiadane moduły (długość, szerokość, rozstaw rzędów pinów ESP32, wysokość kołków) i popraw wartości w słowniku `MODULES` w `plate.py`.
2. Wydrukuj `walle_fit_coupon.stl` (ok. 6 cm³, kilkanaście minut): same obrysy kieszeni z kątownikami. Przyłóż moduły – mają wchodzić z lekkim oporem; jeśli luźno lub ciasno, zmień `CLEAR`.
3. Dopiero potem drukuj `walle_plate.stl`: PETG, warstwa 0,2 mm, 4 ścianki, brim (duża płaska płyta lubi się wyginać – **nie testowane**).
4. Odtworzenie plików po zmianach: `pip install trimesh manifold3d numpy matplotlib scipy networkx && python3 hardware/mounting-plate/plate.py`.
