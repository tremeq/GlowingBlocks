# GlowingBlocks

Plugin Paper do podświetlania bloków, głów i NPC Citizens. Obsługuje 16 kolorów, animację rainbow, prywatne efekty każdego gracza i wspólny efekt globalny. ProtocolLib nie jest wymagany. Zmodyfikowana biblioteka GlowingEntities autorstwa SkytAsul znajduje się w JAR-ze.

## Wymagania i instalacja

- **Paper 1.19.4 lub nowszy**. Spigot, Bukkit i Folia nie są obsługiwane.
- Java co najmniej 17 oraz wersja Javy wymagana przez wybrane wydanie serwera.
- **Citizens jest opcjonalny**, wymagany tylko dla komend NPC. Wybierz wydanie Citizens zgodne z serwerem.

Wymóg 1.19.4 wynika z użycia encji Display bez hitboxa. Poprzednia deklaracja zgodności z 1.17 nie dotyczy tej wersji. Integracja NPC używa wewnętrznych pakietów Minecrafta: zgodność z każdym przyszłym wydaniem nie jest gwarantowana. Błąd jej inicjalizacji jest raportowany w logu i nie wyłącza obsługi bloków.

Zatrzymaj serwer, umieść `target/glowingblocks-2.0.0.jar` w `plugins/` i uruchom serwer. Przy aktualizacji zastąp poprzedni JAR, zachowując katalog `plugins/GlowingBlocks/`. Konfiguracja powstaje automatycznie. `/ge reload` przeładowuje konfigurację; po wymianie JAR-a wymagany jest restart serwera.

## Komendy

| Komenda | Działanie |
| --- | --- |
| `/glowblock [kolor\|rainbow] [private\|global]` | Podświetla wskazany blok; domyślny kolor to RED. |
| `/unglowblock [private\|global]` | Usuwa wybrany efekt ze wskazanego bloku. |
| `/glownpc <id\|nazwa> [kolor\|rainbow] [private\|global]` | Podświetla NPC Citizens, także po jego późniejszym pojawieniu się. |
| `/unglownpc <id\|nazwa> [private\|global]` | Usuwa efekt NPC. Nazwy ze spacjami należy zastąpić ID. |
| `/ge help` | Pomoc. |
| `/ge list` | Liczba zapisanych efektów bloków, animowanych efektów bloków i efektów NPC. |
| `/ge save` | Zapis wszystkich efektów na dysku; czeka na zakończenie zapisu. |
| `/ge reload` | Wczytuje konfigurację i dane, następnie odtwarza widoczne efekty. |
| `/ge version` | Wersja pluginu. |

Aliasy: `/gb`, `/glow`, `/ugb`, `/unglow`, `/gnpc`, `/ugnpc`, `/ge`, `/glowing`. Pełna nazwa komendy administracyjnej to `/glowingentities`. Aliasy można wyłączyć w konfiguracji.

Przykłady:

```text
/gb RED private
/gb RAINBOW global
/ugb private
/glownpc 12 GOLD global
/unglownpc 12 global
```

Kolory: `BLACK`, `DARK_BLUE`, `DARK_GREEN`, `DARK_AQUA`, `DARK_RED`, `DARK_PURPLE`, `GOLD`, `GRAY`, `DARK_GRAY`, `BLUE`, `GREEN`, `AQUA`, `RED`, `LIGHT_PURPLE`, `YELLOW`, `WHITE`. Wielkość liter nie ma znaczenia.

Komendy bloków wymagają gracza patrzącego na blok w zasięgu `defaults.raycast-range`. Konsola może tworzyć efekty NPC w trybie `global`. Komendy zmieniające efekty mają odstęp 0,5 sekundy na gracza.

## Widoczność i uprawnienia

Każdy blok lub NPC może mieć niezależny efekt globalny i osobne efekty prywatne poszczególnych graczy. **Prywatny efekt ma pierwszeństwo przed globalnym dla jego właściciela**. Usunięcie prywatnego efektu ponownie odsłania globalny; nie usuwa efektów innych graczy. Efekty globalne widzą gracze obserwujący dany obiekt.

Bez argumentu widoczności komenda tworzenia korzysta z `defaults.visibility` (domyślnie `private`). Komenda usuwania najpierw wybiera własny efekt prywatny, a jeśli go nie ma — globalny. Operacje globalne wymagają dodatkowego uprawnienia.

Wszystkie poniższe uprawnienia domyślnie przysługują operatorom:

| Uprawnienie | Dostęp |
| --- | --- |
| `glowingblocks.glowblock` | Tworzenie/zmiana efektów bloków. |
| `glowingblocks.unglowblock` | Usuwanie efektów bloków. |
| `glowingblocks.glownpc` | Tworzenie/zmiana efektów NPC. |
| `glowingblocks.unglownpc` | Usuwanie efektów NPC. |
| `glowingblocks.global` | Dodatkowe prawo do edycji i usuwania efektów globalnych. |
| `glowingblocks.admin` | Komendy administracyjne i alternatywne uprawnienie do operacji globalnych. |

## Konfiguracja

Wartości domyślne i teksty komunikatów znajdują się w [config.yml](../src/main/resources/config.yml).

- `rainbow.enabled` i `features.rainbow-animation` muszą być włączone, aby animacja działała. `rainbow.colors` określa kolejność kolorów, a `rainbow.interval-ticks` odstęp zmian (domyślnie 20 ticków). Wyłączenie animacji przywraca zapisany kolor, zachowując dane.
- `features.global-mode: false` ukrywa efekty globalne i blokuje tworzenie nowych, zachowując dane i efekty prywatne.
- `features.npc-glow: false` wyłącza integrację i komendy NPC, zachowując zapisane dane.
- `features.auto-save: true` grupuje zmiany i zleca zapis po 100 tickach; zapisuje też przy wyłączaniu pluginu. Przy `false` używaj `/ge save`.
- `performance.max-blocks-per-player` ogranicza zapisane prywatne efekty bloków jednego gracza. `performance.max-global-blocks` dotyczy globalnych efektów bloków. Wartość `0` blokuje nowe wpisy; istniejące nadal można zmieniać i usuwać.
- `performance.chunk-load-delay` i `performance.player-join-delay` są opóźnieniami w tickach. Plugin nie ładuje chunków wyłącznie w celu pokazania efektów.
- Komunikaty, prefiks i kolory tekstu `&` można dostosować w sekcji `messages`.

Nieprawidłowe kolory animacji, pusta paleta i wartości liczbowe spoza dozwolonego zakresu powodują odrzucenie konfiguracji. Błąd konfiguracji lub pliku danych podczas przeładowania zachowuje aktywne ustawienia i dane.

## Zapis i migracja

Efekty są zapisywane w `plugins/GlowingBlocks/saves.yml`. Format 2 przechowuje świat, współrzędne, typ bloku, kolor, animację i właściciela; wpisy NPC zawierają ID i nazwę. Nazwy światów z kropkami i przecinkami są obsługiwane. Dane nieobecnych światów pozostają zapisane.

Zapis używa osobnego wątku, pliku tymczasowego i atomowej podmiany, jeśli system plików ją obsługuje. Przy wyłączaniu plugin czeka na zapis. Sprzątanie encji wizualnych nie kasuje zapisów.

Starszy format jest wczytywany automatycznie; przed migracją powstaje `saves.yml.v1.bak`. Stare wpisy prywatnych NPC nie zawierały właściciela i były rozsyłane wszystkim: są migrowane jako globalne z ostrzeżeniem w logu. Nie można odzyskać nieistniejącej informacji o właścicielu. Błędny wpis odrzuca wczytanie całego pliku zamiast pomijać dane.

`/ge reload` najpierw zapisuje oczekujące zmiany w pamięci. Ręczną edycję `saves.yml` wykonuj przy zatrzymanym serwerze, aby automatyczny zapis nie nadpisał edycji.

## Renderowanie i zachowanie

Bloki korzystają z `BlockDisplay`, a głowy i obsługiwane bloki z osobnym rendererem (np. skrzynie) z `ItemDisplay`. Encje są nieutrwalane, pozbawione hitboxa i domyślnie ukryte; plugin pokazuje je odpowiednim graczom. Kliknięcia trafiają do rzeczywistych bloków. Głowy graczy zachowują profil tekstury. Plugin nie tworzy slime'ów ani shulkerów i nie przechwytuje kliknięć.

Obrys wynika z modelu Display. Dla bloków renderowanych jako przedmiot model może różnić się od rzeczywistego bloku, np. przy otwieraniu lub łączeniu skrzyń. Pakiety zasobów również mogą zmienić wygląd. Nie jest to gwarancja idealnego odwzorowania każdego specjalnego modelu Minecrafta.

Zmiana typu lub zniszczenie obserwowanego bloku usuwa jego zapisane efekty. Tłoki usuwają efekty przenoszonych bloków; efekt nie podąża za blokiem. Zmiana stanu bloku odświeża wizualizację. Wyjście gracza, zmiana świata, odładowanie chunku i wyłączenie pluginu sprzątają encje wizualne.

NPC korzystają z kolorowych drużyn i flagi świecenia wysyłanych indywidualnie do odbiorców. Drużyny świecenia mają wyłączoną widoczność nazw (`NAME_TAG_VISIBILITY: NEVER`), również podczas animacji rainbow. Plugin nie zmienia zapisanych nazw ani metadanych nazw Citizens. Despawn zachowuje efekt do kolejnego spawnu; trwałe usunięcie NPC usuwa jego efekty.

## Budowanie i weryfikacja

```text
mvn clean verify
```

Wynik: `target/glowingblocks-2.0.0.jar`. Testy jednostkowe obejmują zapis, migrację, prywatne/globalne warstwy, błędne dane, przeładowanie i walidację konfiguracji. Testy na działającym serwerze i ich ograniczenia opisuje [TESTING.md](../TESTING.md).

## Autorzy i licencja

Autor pluginu: TremeQ. Biblioteka [GlowingEntities](https://github.com/SkytAsul/GlowingEntities): SkytAsul, MIT; dołączona wersja zawiera lokalne poprawki obsługi pakietów i sprzątania efektów. Treść licencji znajduje się w [LICENSE](../LICENSE) oraz wewnątrz JAR-a.
