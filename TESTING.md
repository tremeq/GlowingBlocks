# Weryfikacja GlowingBlocks

Data: 2026-10-02.

## Build i testy jednostkowe

`mvn -o -B clean verify` zakończyło się `BUILD SUCCESS`: **18 testów, 0 błędów, 0 pominiętych**. Opcja `-o` korzysta z zależności wcześniej pobranych do lokalnego repozytorium; przy pierwszym budowaniu użyj `mvn -B clean verify`.

- 15 testów `DataManagerTest`: niezależne warstwy prywatne/globalne, zapis i odczyt, dane NPC, migracja z kopią, nietypowe nazwy światów, wyłączanie, automatyczny/ręczny zapis, odtwarzanie usuniętego pliku, odrzucanie błędnego YAML, błędnych wpisów i nieprawidłowej wersji formatu.
- 3 testy `PluginSettingsTest`: paleta, przełączniki, zakresy i limity konfiguracji.

Kod kompilowany jest z `release 17` przeciwko API Paper 1.19.4. Sprawdzono zawartość JAR-a: klasa startowa, zasoby, mapowania i licencja są obecne; usunięty listener przechwytujący interakcje nie jest pakowany. `git diff --check` nie wykazał błędów białych znaków.

## Testy na serwerze

Oddzielny lokalny serwer **Paper 1.20.4 build 336**, Java 21, Citizens **2.0.33 build 3382**, dwóch klientów protokołu Mineflayer. Testy odbyły się na testowym świecie i porcie dostępnym wyłącznie przez localhost. Nie podmieniano pluginu na docelowym serwerze użytkownika.

Potwierdzono asercjami klientów i odczytem zapisu po zatrzymaniu:

1. Prywatny obrys bloku trafia tylko do właściciela; drugi gracz może mieć niezależny obrys tego samego bloku.
2. Usunięcie prywatnej warstwy odsłania globalną i zachowuje prywatną warstwę drugiego gracza.
3. `/ge reload` odtwarza efekty dla obu graczy.
4. Podświetloną skrzynię można otworzyć. Głowa w trybie rainbow tworzy encję Display.
5. Prywatne świecenie NPC jest wysyłane tylko właścicielowi; usunięcie przywraca flagi encji bez świecenia.
6. Globalne rainbow NPC zostaje zapisane, a po restarcie flagę świecenia otrzymują obaj gracze.
7. Prywatna warstwa NPC pozostaje po usunięciu globalnej; usunięcie prywatnej przywraca istniejącą globalną.
8. Bloki i NPC odtwarzają się po ponownym wejściu w zasięg śledzenia/chunków.
9. Zmiana stanu skrzyni odświeża wizualizację dla obu graczy, zachowując ich warstwy.
10. Wyłączenie globalnych efektów i integracji NPC przez konfigurację usuwa odpowiednie wizualizacje, zachowując prywatne bloki. Ponowne włączenie odtwarza efekty.
11. Bez JAR-a Citizens plugin startuje, odtwarza zapisane bloki, obsługuje chunki i przeładowanie; komendy NPC informują o niedostępnej integracji. Zapis NPC pozostaje zachowany.
12. Zatrzymanie serwera nie usuwa zapisanych efektów bloków ani NPC.

Wszystkie przebiegi zakończyły się `RESULT failures=0`. Końcowy JAR przeszedł dodatkowy przebieg restartu, warstw NPC, zmiany stanu bloku, odtwarzania chunków i przełączników konfiguracji. W jego logu serwera nie stwierdzono wyjątków ani błędów pluginu.

## Zakres potwierdzenia

Klienci testowi sprawdzali encje, pakiety, flagi i interakcje; nie renderowali obrazu. Dokładnego wyglądu obrysu, tekstur głów i wszystkich specjalnych modeli nie oceniano w graficznym kliencie Minecrafta. Obsługę rainbow uruchomiono, lecz nie wykonywano pomiaru wizualnej płynności ani porównania każdego koloru.

Test działania dotyczy podanej wersji Paper/Citizens. Nie wykonano testów obciążeniowych ani pełnej macierzy wersji 1.19.4–najnowsza, pakietów zasobów, wszystkich typów NPC i interakcji z innymi pluginami scoreboard. Minimalne API 1.19.4 zostało sprawdzone przez kompilację.

Po poprawce widoczności nazw ponownie wykonano `mvn -o -B verify` (18 testów bez błędów) i test dwóch klientów na tym samym serwerze. Przechwycone pakiety tworzenia drużyn świecenia miały `nameTagVisibility: never` dla efektów prywatnych, globalnych i wszystkich siedmiu kolorów rainbow. Przebieg obejmował także restart, przeładowanie i ponowne wejście w zasięg NPC. Jest to weryfikacja pakietów, bez oceny obrazu w kliencie graficznym.

JAR po poprawce nazw: `target/glowingblocks-1.0.0.jar` (146170 bajtów).

SHA-256:

```text
000793d2fe6c02a978160de04200e3d4d537c0fdce77e9e818a186e0b2aa8811
```
