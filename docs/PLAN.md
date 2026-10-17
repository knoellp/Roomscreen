# Projektauftrag: Roomscreen

> Auftrag an Claude Code. Arbeite selbstständig, aber halte dich an die Reihenfolge der Phasen. Prüfe alle hier genannten technischen Annahmen selbst an der Quelle, bevor du darauf aufbaust. Wo eine Angabe in diesem Dokument als „zu prüfen“ markiert ist, ist sie ausdrücklich nicht verifiziert.

## 0. Projektidentität und Einstieg

### Name

- **Projektname:** Roomscreen
- **Untertitel:** *A Now Playing display for every room – for Roon on Fire TV and Android TV*
- **Repository:** `roomscreen` (GitHub, öffentlich)
- **Application ID:** `io.github.knoellp.roomscreen`
- **Sprache:** Code, Kommentare, Commits, README und UI-Texte auf Englisch. Die UI-Texte zusätzlich auf Deutsch über String-Ressourcen.

### Markenrecht

„Roon“ ist eine Marke von Roon Labs. Der Name kommt deshalb nur beschreibend vor („for Roon“), nie als Teil des Produktnamens oder Logos. In die README gehört der Hinweis: „Roomscreen is not affiliated with or endorsed by Roon Labs. Roon is a trademark of Roon Labs LLC.“

### Positionierung

In der README kurz und sachlich abgrenzen:
- **Nativ statt Web-Display:** Läuft direkt als App auf dem Fire TV, ohne Docker oder Server.
- **Für die Fernbedienung gebaut:** Bedienung per Steuerkreuz und Media-Tasten.
- **Synchronisierte Liedtexte** über LRCLIB.
- **Open Source.**

### Erste Schritte für Claude Code

1. Dieses Dokument nach `docs/PLAN.md` legen.
2. Eine knappe `CLAUDE.md` anlegen: Projektzweck, Build- und Testbefehle, Verweis auf `docs/PLAN.md`, die harten Randbedingungen aus Abschnitt 1 und die Testregeln aus Abschnitt 10.
3. Umgebungscheck (Abschnitt 10, „Testumgebung“) durchführen und Ergebnis melden.
4. Recherche und Architekturentscheidung zur Roon-Anbindung (Abschnitt 3) treffen, in `docs/ARCHITECTURE.md` festhalten. Danach Phase 1.
5. Vor Phase 1 von mir abfragen bzw. bestätigen lassen:
   - Fire-TV-IP
   - Name der Testzone in Roon
   - Fire-OS-Version des Geräts
   - Lizenzwahl (Vorschlag: Apache-2.0, kompatibel zur Lizenz von node-roon-api)

## 1. Ziel

Eine eigenständige, native Android-TV-App, die anzeigt, was in einer bestimmten Roon-Zone gerade läuft – großflächig, ruhig und hochwertig (Stilrichtung: Roon, Apple Music, TIDAL). Optional: einfache Wiedergabesteuerung.

Die App spielt selbst keine Musik ab.

### Harte Randbedingungen

- Direkte Kommunikation App ↔ Roon Server. Kein Node.js-Dienst, kein Docker-Container, keine Roon-Bridge, kein sonstiger Hilfsdienst.
- Native Oberfläche (Kotlin, Jetpack Compose for TV). Keine WebView.
- Auslieferung als sideloadbare APK.
- Keine Platzhalter als fertige Funktion ausgeben. Was nicht implementiert oder nicht getestet ist, wird in der README ausdrücklich so benannt.

## 2. Zielgeräte

### Primär: Fire TV Stick 4K (Android-basiertes Fire OS)

Das vorhandene Gerät ist sideloadfähig; Installation eigener APKs per ADB ist bereits erprobt. Entwicklung, Debugging und Abnahme laufen über ADB auf diesem Gerät.

`minSdk` anhand der Fire-OS-Version dieses Geräts festlegen (in den Geräteeinstellungen ablesen; zu prüfen, welchem Android-API-Level sie entspricht).

Hinweis: Der Fire TV Stick 4K Select läuft auf Vega OS (kein Android) und wird nicht unterstützt.

### Sekundär: Echo Show 15 – Verteilung über Live App Testing

Auf dem Echo Show 15 gibt es kein ADB und kein Sideloading. Amazon nennt für das Testen eigener Apps auf diesem Gerät ausdrücklich den Weg über **Live App Testing (LAT)** im Amazon Developer Console: Die APK wird hochgeladen und nur an selbst definierte Tester (eigener Amazon-Account) über den Appstore ausgeliefert, ohne öffentliche Veröffentlichung.

Konsequenzen für das Projekt:

- **Kein ADB auf dem Echo Show:** Debugging dort nur indirekt. Logs und Fehlerzustände müssen in der App selbst sichtbar sein (verstecktes Diagnose-Overlay: Verbindungsstatus, Core-Adresse, Discovery-Ergebnis, letzte Fehler).
- **Fire OS 7 auf dem Echo Show 15** (laut Amazon-Entwicklerblog 2022, damals mit Ziel-API-Level 28) – aktuelle Version auf dem Gerät prüfen; `minSdk` muss beide Geräte abdecken.
- **Touch ist dort die primäre Bedienung.** Alle Funktionen müssen sowohl per Fernbedienung als auch per Touch erreichbar sein.
- **LAT-Pflichtangaben vorbereiten:** App-Titel, Kategorie, Icons (für Fire TV 512×512 px und 1280×720 px), Sprachen, Content-Rating, Export-Compliance.
- **Datenschutz:** Amazon akzeptiert keine LAT-Einreichungen, die personenbezogene Daten erheben. Die App darf außer dem lokal gespeicherten Roon-Token und den Einstellungen nichts speichern oder übertragen; an LRCLIB gehen nur Titel-Metadaten. Das in der README dokumentieren.
- **Signierung:** Mit einem festen, eigenen Release-Keystore signieren (nicht dem Debug-Key), damit Updates über LAT und Sideloading konsistent bleiben. Keystore nicht ins Repo.
- **Zu prüfen:** Laufzeitbegrenzung einer LAT-Testphase, Prüf- bzw. Freigabedauer, ob die Testversion unbefristet nutzbar bleibt, und ob der Echo Show 15 als Zielgerät in der Geräteauswahl der Konsole auftaucht.

Die Oberfläche soll ohnehin auflösungsunabhängig gebaut werden (kein harter Pixel-Layout-Zwang), damit eine spätere Portierung auf andere Android-Geräte (Google TV, Android TV, Tablet) möglich bleibt.

Eine Tizen-Version (Samsung The Frame) gehört nicht zum Umfang.

## 3. Roon-Anbindung

### Protokolle

- **SOOD** – UDP-Discovery des Roon Cores. Port 9003, Multicast an 239.255.90.90 sowie Subnetz-Broadcast.
- **MOO** – RPC über WebSocket. In Beispielen und Netzwerkanalysen wird TCP-Port 9330 verwendet. Roon hat den Port in der Vergangenheit geändert (früher 9100) und empfiehlt, ihn per Discovery zu ermitteln statt ihn fest zu codieren.

Referenzen für das Protokoll (zu lesen, bevor Code entsteht):

- `RoonLabs/node-roon-api` – offizielle Referenzimplementierung (Apache-2.0): `sood.js`, `moo.js`, `moomsg.js`, `transport-websocket.js`, `core.js`, plus die Services `node-roon-api-transport` und `node-roon-api-image`.
- `shin1ohno/roon-rs` – Rust-SDK (MIT/Apache-2.0) mit separaten Protokollbeschreibungen `docs/protocols/SOOD.md` und `docs/protocols/MOO.md`.

### Pflichtfunktionen

1. Core per SOOD finden.
2. Fallback: manuelle Eingabe von Host und Port (Default 9330, editierbar) mit Verbindungstest. Zuletzt erfolgreichen Host:Port lokal speichern.
3. Als Extension registrieren (Transport- und Image-Service anfordern).
4. Ersteinrichtung: Hinweis auf dem Bildschirm, dass die Extension in Roon unter den Extension-Einstellungen aktiviert werden muss. Den exakten Menüpfad der aktuellen Roon-Version prüfen (Quellen nennen sowohl „Settings → Extensions“ als auch „Settings → Setup → Extensions“).
5. Autorisierungstoken persistent speichern.
6. Zonen abonnieren (`subscribe_zones`), Änderungen inkrementell verarbeiten.
7. Albumcover über den Image-Service laden.
8. Steuerung: Play/Pause, Next, Previous für die gewählte Zone.
9. Automatische Wiederverbindung mit Backoff nach Verbindungsabbruch, Core-Neustart oder WLAN-Unterbrechung.

### Zu klärende Punkte (vor der Implementierung)

- **Multicast auf Fire OS:** Ob für den Empfang der SOOD-Antworten ein `WifiManager.MulticastLock` nötig ist und ob Fire OS Multicast überhaupt zuverlässig durchlässt. Der manuelle Fallback muss daher von Anfang an vollwertig sein, nicht nachgelagert.
- **Mehrere Installationen:** Wie Roon mehrere Instanzen derselben Extension (gleiche `extension_id`) auf verschiedenen Geräten behandelt – erscheinen sie getrennt, teilen sie sich eine Autorisierung, greift der Pairing-Mechanismus? Ziel: Wohnzimmer-Fire-TV und ein zweites Gerät laufen unabhängig voneinander. Falls nötig, eine geräteindividuelle Kennung in `display_name` aufnehmen.
- **Seek-Updates:** Prüfen, wie oft der Core Positionsänderungen (`zones_seek_changed`) pusht. roon-rs drosselt diese Ereignisse in seinem CLI auf 1 Hz pro Zone, was darauf hindeutet, dass der Core sie laufend sendet. Die lokale Interpolation (siehe 5.) dient dann nur der flüssigen Darstellung zwischen den Ereignissen, nicht dem Ersetzen von Abfragen.

### Implementierungsentscheidung

Bewerte und entscheide begründet zwischen:

| Option | Stand laut Recherche |
|---|---|
| **A: Native Kotlin-Implementierung** von SOOD + MOO + benötigten Services (OkHttp-WebSocket, eigener SOOD-Client) | kein zusätzlicher Toolchain-Aufwand; Protokollreferenz durch node-roon-api und roon-rs-Doku vorhanden |
| **B: `roon-api` (roon-rs) via JNI/UniFFI** | MIT/Apache-2.0, Version 0.5.x, kleines Einzelentwicklerprojekt; vorgefertigte Binaries gibt es nur für Linux/macOS/Windows (das betrifft das CLI, nicht die Frage, ob die Bibliothek für Android kompiliert). Android-Build und TLS-Abhängigkeiten (reqwest) sind zu prüfen |
| **C: `rust-roon-api` (TheAppgineer) via JNI** | MIT, ausdrücklich Alpha, nicht auf crates.io; wird aber von Community Remote (Flutter + Rust über flutter_rust_bridge) auf Android eingesetzt – also nachweislich auf Android lauffähig |

Bewertungskriterien: Implementierungsaufwand für genau den benötigten Umfang (Discovery, Registrierung, Transport, Image), Build-Komplexität (NDK, Cross-Compiling für die CPU-Architektur des Zielgeräts), Wartbarkeit, Lizenz, Debugbarkeit.

Vorgabe: Wenn der benötigte Protokollumfang in Kotlin überschaubar ist, Option A bevorzugen. Die Entscheidung mit Begründung in `docs/ARCHITECTURE.md` festhalten.

## 4. Zonenauswahl

- Nach erfolgreicher Autorisierung: Liste der verfügbaren Zonen.
- Auswahl wird lokal (DataStore) gespeichert; beim nächsten Start automatisch wieder verwenden.
- Wechsel jederzeit über ein Menü.
- Die Auswahl einer Zone darf niemals eine Wiedergabe starten oder andere Zonen verändern.
- Ist die gespeicherte Zone nicht (mehr) vorhanden: Hinweis anzeigen und Zonenliste öffnen, nicht stillschweigend eine andere Zone wählen.

## 5. Oberfläche

### Cover-Ansicht (Standard)

- Vollbild. Links großes Albumcover, rechts Titel, Künstler, Album, Fortschrittsbalken, verstrichene Zeit, Gesamtdauer, Wiedergabestatus.
- Hintergrund: stark vergrößertes, weichgezeichnetes Cover mit dunklem Verlauf für Lesbarkeit. Auf dem Zielgerät auf Performance prüfen; falls Blur zu teuer ist, das Hintergrundbild einmal pro Titel vorberechnen statt live zu rendern.
- Weiche, unaufdringliche Überblendung bei Titelwechsel.
- Fortschritt: letzte empfangene Position + lokale monotone Uhr interpolieren; bei Pause, Seek, Titelwechsel und neuem Seek-Ereignis korrigieren.
- Platzhalter für fehlendes Cover und für leere bzw. gestoppte Zonen.

### Fernbedienung

- Vollständig per Steuerkreuz, Auswahl-, Zurück- und Media-Tasten bedienbar.
- Play/Pause, Next, Previous über die entsprechenden Fernbedienungstasten.
- Bedienelemente standardmäßig ausgeblendet, erscheinen bei Interaktion.
- Bildschirm darf während der Anzeige nicht in den Bildschirmschoner/Standby gehen (`keepScreenOn` während Wiedergabe; Verhalten bei pausierter oder leerer Zone festlegen).

## 6. Synchronisierte Liedtexte (Phase 3)

Quelle: LRCLIB (`https://lrclib.net/docs`).

- Primär `GET /api/get` mit `track_name`, `artist_name`, `album_name`, `duration` (Sekunden). LRCLIB liefert bei angegebener Dauer nur Treffer mit höchstens ±2 s Abweichung – das ist der zentrale Schutz gegen falsche Versionen (Live, Remix).
- `/api/search` nur als Fallback; laut Doku langsamer und unpräziser. Treffer aus der Suche zusätzlich selbst nach Dauer filtern.
- Eigenen, aussagekräftigen `User-Agent` mit Projektname, Version und Repo-URL setzen.
- Antwortfelder `syncedLyrics` (LRC) und `plainLyrics` auswerten.
- LRC-Parser mit Unit-Tests (mehrere Zeitstempel pro Zeile, Offsets, leere Zeilen, Metadaten-Tags).
- Ergebnisse und Fehlschläge lokal cachen (Schlüssel: Titel/Künstler/Album/Dauer), damit dieselbe Anfrage nicht wiederholt wird.
- Laufende Abfrage abbrechen bzw. verwerfen, wenn währenddessen der Titel wechselt.
- Keine Texte vorhanden → normale Cover-Ansicht, keine Fehlermeldung.

### Lyrics-Ansicht

Kleineres Cover links, rechts großformatige Zeilen, aktive Zeile hervorgehoben, vorherige und folgende sichtbar, automatisches Scrollen. Nur unsynchronisierte Texte → statische Darstellung. Umschaltung Cover ↔ Lyrics per Fernbedienung, letzte Wahl wird gespeichert.

## 7. Stabilität

Die App läuft potenziell stundenlang ohne Interaktion. Abzudecken:

- Verbindungsabbruch, Core-Neustart, WLAN-Unterbrechung → selbstständige Wiederherstellung.
- Zonenwechsel, leere Zonen, fehlende Cover.
- Android-Lifecycle (App in den Hintergrund und zurück): Verbindung sauber pausieren bzw. wieder aufnehmen, keine Doppelverbindungen.
- Kein Speicherleck bei vielen Titelwechseln (Bild-Cache begrenzen).
- Ressourcenschonend genug für den Fire TV Stick.

## 8. Technik

| Komponente | Vorgabe |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose for TV |
| Architektur | MVVM, schlicht |
| Nebenläufigkeit | Coroutines / Flow |
| Einstellungen | DataStore |
| HTTP/WebSocket | OkHttp |
| Bilder | Coil (Kompatibilität mit Compose for TV prüfen) |
| Build | Gradle, Ausgabe: APK |

Aktuelle, untereinander kompatible Versionen verwenden und im Build tatsächlich verifizieren. Möglichst wenige Abhängigkeiten. Keine unnötige Abstraktion.

## 9. Phasen

### Phase 1 – Roon-Verbindung (Vorrang vor allem anderen)

Discovery, manueller Fallback, Registrierung, Autorisierung, Token-Speicherung, Zonenauswahl, Empfang der Now-Playing-Daten. Minimale Anzeige (Titel, Künstler, Album, Cover) genügt.

Zusätzlich: Die Roon-Client-Logik in ein reines Kotlin/JVM-Modul ohne Android-Abhängigkeiten legen und ein kleines Kommandozeilen-Testprogramm dazu bauen, das ich auf meinem Rechner im Heimnetz gegen den echten Core laufen lassen kann (finden, verbinden, Zonen auflisten, Now Playing ausgeben). So ist die Protokollimplementierung testbar, bevor sie auf dem Fire TV landet.

### Phase 2 – TV-Oberfläche

Vollständige Cover-Ansicht, Hintergrund, Fortschritt, Steuerung, Fernbedienungsnavigation.

### Phase 3 – Liedtexte

LRCLIB, LRC-Parser, Synchronisation, Lyrics-Ansicht.

### Phase 4 – Feinschliff

Animationen, Performance auf dem Zielgerät, Fehlerbehandlung, Wiederverbindung.

### Phase 5 – Echo Show 15

Touch-Bedienung vervollständigen, Diagnose-Overlay, Release-Signierung, Checkliste und Anleitung für die Einreichung über Live App Testing (Abschnitt 2). Die Einreichung selbst nehme ich in der Developer Console vor.

## 10. Teststrategie – Claude Code testet selbst

Grundregel: Eine Funktion gilt erst als fertig, wenn Claude Code sie selbst automatisiert geprüft hat und den Nachweis vorweisen kann: grüner Testlauf, Screenshot oder Log-Auszug. „Kompiliert“ ist kein Nachweis.

### Testumgebung

Claude Code läuft auf einer Ubuntu-VM in meinem Heimnetz. Die VM muss im selben LAN-Segment wie Roon Core und Fire TV hängen (Bridged Networking, eigene LAN-IP).

Als allererster Schritt in Phase 1 wird die Umgebung geprüft:

1. `adb connect <Fire-TV-IP>:5555` erfolgreich. Beim ersten Mal muss ich „Allow USB debugging?“ auf dem Fernseher bestätigen, mit „Always allow from this computer“. Danach `adb devices` zeigt das Gerät als `device`.
2. SOOD-Discovery von der VM aus findet den Core. Falls nicht: Netzwerkkonfiguration der VM melden, nicht mit hartcodierter IP darüber hinweggehen.
3. TCP-Verbindung zum Core-Port klappt.
4. Toolchain installiert:
   - JDK 17 oder höher
   - Android SDK Command-line Tools und Platform-Tools
   - Maestro CLI (Linux-Installer)

Kein Emulator nötig. Getestet wird auf dem echten Gerät, UI-Screenshots entstehen auf der JVM.

### Build-Install-Test-Schleife (ohne mein Zutun)

Claude Code installiert jede neue Version selbst und testet sie.

**Ein Skript `scripts/deploy-test.sh`:**
1. ADB-Verbindung prüfen, bei Bedarf `adb connect` erneut ausführen.
2. Bauen.
3. `adb install -r` als Update, damit der gespeicherte Roon-Token erhalten bleibt.
4. App starten.
5. Maestro-Flow ausführen.
6. Screenshot und Logcat-Auszug ablegen.

**Regeln:**
- **Signierschlüssel bleibt immer derselbe.** Ändert er sich, schlägt das Update fehl. Die App müsste dann deinstalliert werden, und der Token und die Roon-Freigabe gingen verloren.
- **Keine Deinstallation, kein Zurücksetzen der App-Daten** ohne Rückfrage.
- **Fire TV braucht eine feste IP** (DHCP-Reservierung im Router; richte ich ein). Die IP steht in einer lokalen, nicht eingecheckten Konfigurationsdatei.

### Ebene 1 – Unit-Tests (JVM, ohne Gerät)

`./gradlew test`. Abgedeckt werden:
- SOOD-Serialisierung und -Parsing
- MOO-Nachrichtenformat
- Zonen-Update-Logik
- Fortschrittsinterpolation, mit injizierbarer Uhr statt Echtzeit
- LRC-Parser und Lyrics-Zuordnung nach Dauer
- Reconnect-Backoff

### Ebene 2 – Fake Roon Core (Integrationstest, ohne Gerät)

Ein kleiner Test-Server im Testmodul, der SOOD-Antworten und MOO-WebSocket-Nachrichten nachbildet. Die Antworten werden aus echten, aufgezeichneten Nachrichten des Cores abgespielt; Aufzeichnung siehe Ebene 4.

Damit lassen sich ohne echten Core testen:
- Registrierung, Autorisierung und Token-Wiederverwendung
- Zonenliste und Titelwechsel
- Pause, Seek und leere Zone
- fehlendes Cover
- Verbindungsabbruch und Core-Neustart

Pflicht für alle Stabilitätsszenarien aus Abschnitt 7.

### Ebene 3 – UI- und Screenshot-Tests (JVM, ohne Gerät)

Compose-UI-Tests unter Robolectric, Screenshots mit Roborazzi (`captureRoboImage`). Geprüft werden:
- Cover- und Lyrics-Ansicht
- Zonenauswahl, Ersteinrichtung und Fehlerzustände
- Fokus-Navigation per D-Pad-Key-Events
- verschiedene Auflösungen: Fire TV 1080p und Echo-Show-Format

Claude Code öffnet die erzeugten PNGs und beurteilt das Layout selbst. Die Referenzbilder liegen im Repo und dienen als Regressionstests.

### Ebene 4 – Echtes Gerät + echter Core (das Playwright-Äquivalent)

Fire TV per `adb connect <IP>:5555`. Claude Code steuert und prüft selbst:

- **Installation und Start:** `./gradlew installDebug` bzw. `adb install -r`, App per `adb shell am start` starten.
- **Bedienung:** Maestro-Flows (YAML) mit den Android-TV-Tasten Remote Dpad Up/Down/Left/Right/Center und Remote Media Play Pause/Next/Previous.
- **Sichtkontrolle:** `adb shell screencap -p` + `adb pull`; Claude Code wertet den Screenshot selbst aus.
- **Logs:** `adb logcat` mit eigenem Tag. Die App loggt Verbindungsstatus, Discovery, Zonenwechsel und Titelwechsel strukturiert, sodass Claude Code Zustände aus dem Log verifizieren kann.
- **Debug-Hooks** (nur Debug-Build): Intent bzw. Broadcast, um den Zustand als JSON auszugeben, den gespeicherten Zustand zurückzusetzen oder einen manuellen Core-Host zu setzen.
- **Gegenseite steuern:** Für End-to-End-Tests muss Claude Code auch Musik auf der Testzone starten, pausieren und weiterschalten können. Dafür das JVM-CLI aus Phase 1 um Transport-Befehle erweitern (play, pause, next für eine Zone). Ablauf: Titel per CLI wechseln → Screenshot und Log auf dem Fire TV prüfen.
- **Aufzeichnung:** Das CLI zeichnet echte MOO-Nachrichten in Dateien auf; diese dienen als Fixtures für Ebene 2.

Eine eigene Testzone in Roon verwenden, damit die Tests keine laufende Wiedergabe im Haus stören.

Für jede Phase gehört mindestens ein Maestro-Flow dazu, der die Abnahmekriterien dieser Phase auf dem Fire TV automatisiert abdeckt.

### Nicht automatisierbar

- **Echo Show 15:** kein ADB; Installation nur über Live App Testing. Dort teste ich manuell. Ebene 3 mit Echo-Show-Auflösung und Touch-Tests ist der automatisierte Ersatz.
- **Erstautorisierung in Roon:** Die Freigabe der Extension in den Roon-Einstellungen mache ich einmalig selbst; Claude Code wartet darauf und prüft danach per Log, ob der Token gespeichert wurde.

## 11. Erwartete Ergebnisse

1. `docs/ARCHITECTURE.md` mit der begründeten Entscheidung aus Abschnitt 3.
2. Vollständiges Android-Studio-Projekt, sinnvoll strukturiert.
3. Funktionierende direkte Roon-Kommunikation.
4. Native TV-Oberfläche.
5. Unit-Tests mindestens für: SOOD-Nachrichten (Serialisierung/Parsing), MOO-Nachrichtenformat, Zonen-Update-Verarbeitung, Fortschrittsinterpolation, LRC-Parser, Lyrics-Zuordnung nach Dauer.
6. Erfolgreicher Build, installierbare APK.
7. README mit: Installation per ADB auf Fire TV (Entwickleroptionen, ADB-Debugging, `adb connect`, `adb install`), Freigabe in Roon, Ersteinrichtung, Bedienung, bekannte Einschränkungen, Liste der nicht implementierten oder ungetesteten Funktionen.
8. Projekt so vorbereitet, dass es auf GitHub veröffentlicht werden kann (Lizenz, `.gitignore`, keine Secrets/Tokens im Repo). Hinweis in der README, dass das Projekt nicht mit Roon Labs verbunden ist.

Hinweis zur Testbarkeit: Discovery, Autorisierung und Bedienung auf dem Fire TV kannst du in deiner Umgebung nicht selbst testen. Kennzeichne in der README klar, was durch automatische Tests abgedeckt ist und was ich manuell auf dem Gerät prüfen muss.

## 12. Abnahmekriterien Version 1 (ohne Lyrics)

Auf meinem Fire TV Stick 4K:

1. APK per ADB installieren.
2. App starten, Roon Server wird im Heimnetz gefunden – oder nach manueller Eingabe von Host und Port verbunden.
3. App einmalig in Roon als Extension aktivieren.
4. Zone auswählen.
5. Musik auf dieser Zone über Roon starten.
6. Auf dem Fernseher erscheinen automatisch Cover, Titel und Künstler.
7. Bei Titelwechsel aktualisiert sich die Anzeige selbstständig.
8. Play/Pause und Titelwechsel funktionieren über die Fire-TV-Fernbedienung.
9. Nach Beenden und Neustart verbindet sich die App ohne erneute Einrichtung mit derselben Zone.
10. Nach Neustart des Roon Servers verbindet sich die App ohne Eingriff wieder.
