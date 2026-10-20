# Projektauftrag: Roomscreen (v5 – Fire TV abschließen, dann Web-Kern für Samsung Tizen und Fire TV Vega)

> Auftrag an Claude Code. Dieses Dokument ersetzt `docs/PLAN.md` im Repository `knoellp/Roomscreen`. Arbeite selbstständig, aber halte dich an die Reihenfolge der Phasen. Prüfe alle technischen Annahmen selbst an der Quelle, bevor du darauf aufbaust.
>
> Kennzeichnung:
> - **[verifiziert]** – im Repository belegt (Test, Gerätescreenshot, Log oder Live-Beobachtung am Core); Fundstelle steht dabei, meist `docs/ARCHITECTURE.md` oder die Statustabelle in `docs/STATUS.md`.
> - **[geprüft]** – am 17. bzw. 18.10.2026 an der in Abschnitt 16 genannten Quelle nachgelesen, nicht auf dem Gerät erprobt.
> - **[zu prüfen]** – nicht verifiziert. Vor der Verwendung klären und das Ergebnis in `docs/ARCHITECTURE.md` festhalten.
>
> Änderungen gegenüber v4:
> 1. **„Keine WebView“ präzisiert.** Gemeint war: keine Browser-Lösung und keine gehostete Webseite. Eine installierte App, deren Oberfläche aus gebündelten Web-Dateien in einer eingebetteten WebView besteht, ist zulässig (Abschnitt 1).
> 2. **Vega OS als weitere Plattform.** Neue Fire TV Sticks (Fire TV Stick 4K Select, Fire TV Stick HD 2026) laufen mit Vega OS statt Android; die Android-APK läuft dort nicht. Neue Phasen V0–V3 (Abschnitte 6.8–6.11 und 10).
> 3. **Gemeinsamer Web-Kern.** Die Tizen-Implementierung wird von Anfang an als plattformneutraler Web-Kern (`web/`) mit dünner Plattformschicht gebaut. Tizen und Vega sind Hüllen um denselben Kern (Abschnitt 4.4).
> 4. **Fire OS bleibt vorerst Kotlin.** Ob Fire OS später ebenfalls auf den Web-Kern umgestellt wird, entscheide ich nach den Messungen auf Vega (Abschnitt 14, Entscheidung 5).
> 5. **Veröffentlichung im Amazon Appstore** als Option mit den bekannten Hürden beschrieben (Abschnitt 5.3, Entscheidung 4).

## 0. Projektidentität und Einstieg

### Name

- **Projektname:** Roomscreen
- **Untertitel:** *A Now Playing display for every room – for Roon on Fire TV, Android TV and Samsung TV*
- **Repository:** `knoellp/Roomscreen` (GitHub, öffentlich), ein Monorepo für alle Plattformen
- **Android Application ID:** `io.github.knoellp.roomscreen`
- **Tizen-App-ID:** im Tizen-Format `<Paket-ID>.Roomscreen`, Format wie im Samsung-Beispiel `2NTxWwsECr.BasicProject` [geprüft, Samsung WebAssembly-Doku]. Einmal erzeugen und nie mehr ändern.
- **Vega-App-ID:** nach dem Vega-Schema (Paket-ID und Komponenten-ID, z. B. `io.github.knoellp.roomscreen` / `io.github.knoellp.roomscreen.main`); Format und Zusammenhang mit der Android-ID im Appstore **[zu prüfen]**. Einmal festlegen und nie mehr ändern.
- **Lizenz:** Apache-2.0 (liegt im Repo). Mitgelieferte Schrift Comfortaa unter SIL OFL 1.1 (`docs/fonts/comfortaa-OFL.txt`).
- **Sprache:** Code, Kommentare, Commits, README und UI-Texte auf Englisch. UI-Texte zusätzlich auf Deutsch.

### Repository-Struktur

Die bestehende Struktur bleibt; das Gradle-Projekt bleibt im Wurzelverzeichnis.

```
Roomscreen/
├── app/               Android-TV-App (Compose for TV)                          [vorhanden]
├── roon-client/       Roon-Protokoll, Fortschritt, Zonen-Resolver (Kotlin/JVM) [vorhanden]
├── lyrics/            LRC-Parser, Sync, Query-Varianten, LRCLIB-Client, Cache  [vorhanden]
├── roon-cli/          JVM-CLI: Discovery, Zonen, Steuerung, Bilder, Lyrics     [vorhanden]
├── maestro/           Geräte-Flows Fire TV                                     [vorhanden]
├── docker/, scripts/, config/, docs/                                           [vorhanden]
├── shared/            ab Phase F3 (Übergabe)
│   ├── fixtures/      aufgezeichnete MOO-Nachrichten (verschoben aus roon-client/src/test/resources/fixtures)
│   ├── testvectors/   Fälle für Fortschritt, Zonen-Resolver, LRC-Parser, Sync, Query-Varianten, LRCLIB-Antworten
│   ├── design/        Design-Tokens (Farben, Abstände, Schriftgrößen, Hintergrundfarben) als JSON
│   └── strings/       en/de-Texte als JSON
├── web/               ab Phase T2: Web-Kern (TypeScript), plattformneutral
│   ├── core/          Roon-Anbindung, Zustand, Fortschritt, Zonen, Liedtexte – ohne DOM, ohne Plattform-APIs
│   ├── ui/            Oberfläche (DOM/CSS), spricht nur mit der Plattform-Schnittstelle
│   └── platform/      Schnittstelle `Platform` und Desktop-/Test-Implementierung
├── tizen/             ab Phase T1: Tizen-Hülle (config.xml, Plattformschicht, ggf. WebAssembly-SOOD)
└── vega/              ab Phase V1: Vega-Hülle (React Native for Vega mit Vega WebView, ggf. Turbo Module)
```

Fixtures und Testvektoren werden **verschoben**, nicht kopiert; die Kotlin-Tests lesen danach aus `shared/`.

### Markenrecht

„Roon“ ist eine Marke von Roon Labs, „Samsung“ und „Tizen“ von Samsung, „Fire TV“ von Amazon. Namen nur beschreibend („for Roon“), nie im Produktnamen oder Logo. README-Hinweis: „Roomscreen is not affiliated with or endorsed by Roon Labs, Samsung or Amazon. Roon is a trademark of Roon Labs LLC. All other trademarks are the property of their respective owners.“ (Die README nennt derzeit nur Roon Labs; um Samsung und Amazon ergänzen, sobald Tizen dazukommt.)

Für eine Veröffentlichung im Amazon Appstore reicht dieser Hinweis nicht: Amazon verlangt für die Nutzung fremder Marken in App und Metadaten eine Erlaubnis des Rechteinhabers; ein Haftungsausschluss ersetzt sie ausdrücklich nicht [geprüft, Amazon Appstore IP-Richtlinie]. Siehe 5.3.

### Positionierung (README, kurz und sachlich)

- **Installiert statt Web-Display:** läuft als App direkt auf dem Fernseher, ohne Docker, ohne Server, ohne Browser.
- **Für die Fernbedienung gebaut:** Steuerkreuz und Media-Tasten.
- **Synchronisierte Liedtexte** über LRCLIB.
- **Open Source.**

### Erste Schritte für Claude Code

1. **Dieses Dokument als neue `docs/PLAN.md` einchecken** (ersetzt jede frühere Fassung; im Repo liegt noch die v1). Ab jetzt gilt die Phasenbenennung dieses Dokuments (F3, T0 …, V0 …).
2. `CLAUDE.md` anpassen: Projektzweck auf alle Plattformen erweitern (Fire OS, Tizen, Vega), präzisierte WebView-Regel (Abschnitt 1), Toolchain-Ausnahmen für Tizen und Vega, Verweis auf diese Phasen. Bestehende Regeln bleiben, insbesondere Commit-Nachrichten ohne Tool- oder Assistenten-Attribution und ohne Co-Authored-By.
3. README nachziehen: Die letzten Commits vom 18.10. (Liedtext-Timing −3,0 bis +3,0 s, gehärteter Reconnect mit Watchdog und Rediscovery, Hinweis nach 20 s Verbindungsaufbau, verzögertes Trennen beim Verlassen der App) stehen noch nicht in der Status- und Einstellungstabelle. Außerdem ergänzen: Die Fire-TV-App läuft nur auf Geräten mit Fire OS, nicht auf Geräten mit Vega OS (Fire TV Stick 4K Select, Fire TV Stick HD 2026).
4. Mit Phase F3 weitermachen.

## 1. Ziel und harte Randbedingungen

Eine eigenständige, installierbare TV-App, die anzeigt, was in einer bestimmten Roon-Zone gerade läuft – großflächig, ruhig, hochwertig (Stilrichtung: Roon Now Playing, Apple Music auf Apple TV, TIDAL auf dem Fernseher). Dazu einfache Wiedergabesteuerung und synchronisierte Liedtexte. Die App spielt selbst keine Musik ab.

### Harte Randbedingungen (alle Plattformen)

- **Installierte App.** Die App wird auf dem Gerät installiert und aus dessen App-Menü gestartet. Kein Browser, der geöffnet werden muss, keine gehostete Webseite, keine Programmteile, die zur Laufzeit aus dem Netz geladen werden. Eine Oberfläche aus gebündelten HTML-, CSS- und JS-Dateien in einer eingebetteten WebView ist erlaubt.
- **Direkte Kommunikation App ↔ Roon Core zur Laufzeit.** Kein Node.js-Dienst, kein Docker-Container, keine Roon-Bridge, kein Proxy, kein zweiter Rechner im Betrieb. Netzwerkzugriffe nur zum Roon Core und zu LRCLIB.
- Werkzeuge, die nur auf der Entwicklungs-VM laufen (`roon-cli`, Skripte), sind erlaubt und dürfen nie Voraussetzung für den Betrieb werden.
- **Fire OS (Android):** sideloadbare APK. Version 1 bleibt die native Kotlin/Compose-App. Eine spätere Hülle mit WebView und gebündeltem Web-Kern ist zulässig (Entscheidung 5 in Abschnitt 14).
- **Tizen:** paketierte Tizen-Web-App (`.wgt`) mit gebündeltem Web-Kern.
- **Vega:** Vega-App (`.vpkg`) mit Vega WebView und gebündeltem Web-Kern.
- Keine Platzhalter als fertige Funktion ausgeben. Was nicht implementiert oder nicht auf dem echten Gerät getestet ist, steht in der README ausdrücklich so.
- Keine Telemetrie, kein Tracking.

### Toolchain-Regeln (in `CLAUDE.md` übernehmen)

- **Android:** ausschließlich im Docker-Image über `scripts/dock`, kein lokales JDK oder SDK, kein sudo.
- **Tizen (ab Phase T1):** Ausnahme von der Docker-Regel. Tizen SDK samt TV-Erweiterung und die Samsung tizen-agent-skills werden im Home-Verzeichnis des Benutzers installiert (die Skills installieren das SDK selbst, siehe 6.5). Voraussetzung Node.js 20+.
- **Vega (ab Phase V0):** Ausnahme von der Docker-Regel. Vega SDK und CLI im Home-Verzeichnis des Benutzers; Installationsweg und Linux-Voraussetzungen **[zu prüfen]**.
- **Web-Kern:** Node.js 20+, Abhängigkeiten projektlokal (`npm ci`), keine globalen Pakete.
- Überall: kein sudo. Braucht ein Schritt sudo oder eine systemweite Installation: anhalten und mich fragen.

## 2. Zielgeräte und Reihenfolge

| Reihenfolge | Gerät | Plattform | Installation |
|---|---|---|---|
| 1 | Fire TV Stick HD (AFTSS „sheldon“, 2020) | Fire OS 7.7.1.7, SDK 28, armeabi-v7a, 1920×1080 [verifiziert, `ARCHITECTURE.md` 7]. `minSdk 28`. | ADB |
| 2 | Samsung The Frame, Baujahr 2022 | Tizen; laut Samsung-Tabelle Modelljahr 2022 = Tizen 6.5 mit Chromium M85 [geprüft]. Auf dem Gerät bestätigen. | Entwicklermodus + Samsung-Zertifikat, `sdb`/Tizen-CLI |
| 3 | Fire TV Stick mit Vega OS (Fire TV Stick HD 2026 oder Fire TV Stick 4K Select) | Vega OS (Linux-basiert, kein Android) [geprüft, Amazon]. Gerät muss beschafft werden. | Entwicklermodus über das Amazon-Entwicklerkonto, Vega CLI; zusätzlich Vega Virtual Device [geprüft, Amazon Vega-Doku] |
| 4 | Echo Show 15 | Fire OS 7 laut Amazon-Entwicklerblog 2022 | Live App Testing (5.2) |

Achtung Namensgleichheit: Der vorhandene „Fire TV Stick HD“ (AFTSS, 2020) läuft mit Fire OS; der gleichnamige Stick von 2026 läuft mit Vega OS. Laut Amazon laufen ab dem Fire TV Stick 4K Select alle künftigen Fire TV Sticks unter Vega [geprüft]. Fernseher mit Fire TV Edition laufen derzeit weiter mit Fire OS.

Die Oberfläche bleibt auflösungsunabhängig (16:9, 1080p und 4K, Echo-Show-Format).

### Mehrere Displays [verifiziert, `ARCHITECTURE.md` 7]

Registriert sich eine zweite Verbindung mit derselben `extension_id`, bekommt die ältere einen leeren Frame und wird getrennt; beide verdrängen sich dann abwechselnd. Deshalb hängt die Android-App eine einmal erzeugte, gespeicherte Kennung pro Installation an (`io.github.knoellp.roomscreen.<installId>`), und jede Installation braucht ihr eigenes „Enable“ in Roon. Der Web-Kern macht es genauso (6.2). Noch nicht getestet ist der Betrieb zweier echter Geräte nebeneinander über Stunden (Phase F3 mit `roon-cli` als zweiter Instanz, endgültig in den Phasen T5 und V3).

## 3. Stand am 18.10.2026

Quelle: Statustabelle in `docs/STATUS.md` und `docs/ARCHITECTURE.md` 7–9. Die Gerätenachweise (Screenshots, Logs) liegen laut Doku unter `build/device-artifacts/` und sind nicht eingecheckt. Ich selbst (Claude im Chat) konnte die Tests in meiner Umgebung nicht ausführen; die Angaben unten geben die Doku im Repo wieder.

### Fire TV – auf dem Gerät nachgewiesen

- SOOD-Discovery mit `MulticastLock` (Core in etwa 4 s gefunden); Start über den zuletzt bekannten Endpunkt in etwa 20 ms, Discovery nur als Rückfall.
- Registrierung, einmaliges „Enable“, Token-Wiederverwendung nach Neustart.
- Zonenliste, gespeicherte Zone, Zonenwechsel über die Steuerleiste; Roon-Gruppen werden über Output-IDs verfolgt.
- Now Playing: Cover per HTTP, weichgezeichneter Hintergrund (einmal pro Titel vorberechnet, kein Live-Blur, weil `RenderEffect` API 31 braucht), Infoleiste, interpolierter Fortschritt (Seek-Ereignisse kommen mit 1,00 Hz), 0,47 % ruckelnde Frames über 60 s Wiedergabe.
- Media-Tasten über eingespeiste Tastencodes (85, 87, 88, 89, 90); Befehle kommen am Core an. Steuerleiste mit Ausblenden nach 5 s.
- Einstellungen mit Persistenz über Neustart: Hintergrund (Cover, Künstlerbild, Farbe), Schärfe (weich/scharf), Hintergrundfarbe, Steuerleiste, Bildschirm an, „via roon“-Hinweis, LRCLIB an/aus, Core neu suchen, Zone wechseln.
- Bildschirm bleibt an (`FLAG_KEEP_SCREEN_ON`, per `dumpsys power` belegt).
- Synchronisierte Liedtexte: Abfrage, Abbruch bei Titelwechsel, keine Anfrage bei ausgeschaltetem Schalter, Cache über Neustart, aktive Zeile folgt der Wiedergabe.

### Fire TV – nur automatisiert oder gar nicht geprüft

| Punkt | Stand |
|---|---|
| Physische Fire-TV-Fernbedienung | nie gedrückt; nur eingespeiste Codes |
| Reconnect nach Neustart des echten Roon Cores | erledigt 18.10.2026 am echten Core (Softwareneustart, Büro spielend und pausiert, Zone nach 41 s wieder da); `ARCHITECTURE.md` 10.2 |
| Manuelle Host/Port-Eingabe | nur Screenshot |
| „Bildschirm an: nur während Wiedergabe“ | erledigt 18.10.2026 per `dumpsys power`; `ARCHITECTURE.md` 10.4 |
| Liedtexte unsynchronisiert und „No lyrics found“ | nur Screenshot/Test |
| Ruckeln der Liedtext-Ansicht und des scharfen Hintergrunds | erledigt 18.10.2026, unter 1 % ruckelnde Frames warm; `ARCHITECTURE.md` 10.3 |
| Speicher mit Liedtexten | nach 10 Titelwechseln 10–50 MB über dem Wert ohne Liedtexte, überwiegend GPU-Cache; die Toleranz ±10 MB wird **nicht** eingehalten (`ARCHITECTURE.md` 9.5) |
| Speicher scharfer Hintergrund | 128 → 166 → 154 MB (weich: 136–138 MB) |
| Speicher-Dauerlauf (2 h, Liedtexte an, scharfes Künstlerbild) | erledigt 18.10.2026: 13 Proben, PSS 194 MB nach Warm-up, 157 MB am Ende, kein Trend, kein Reconnect; `ARCHITECTURE.md` 10.5 |
| Zwei Geräte gleichzeitig | nicht getestet |
| Release-Signierung | nicht vorhanden; es wird der Debug-Schlüssel genutzt |
| Echo Show 15 | nicht begonnen |
| Geräte mit Vega OS | nicht unterstützt; die APK läuft dort nicht |

### Bekannte externe Einschränkung

LRCLIB antwortet häufig mit `503 ServerOverloaded`, unabhängig vom Titel [verifiziert, `ARCHITECTURE.md` 9.2]. Die App wiederholt zweimal (nach 1 s und 3 s), zeigt dann die normale Ansicht und fragt beim nächsten Titel erneut. Auf manchen Titeln erscheint deshalb kein Text. Das ist kein Fehler der App und muss in der README so stehen (steht dort bereits).

## 4. Gemeinsame Basis

### 4.1 Roon-Protokoll

Maßgeblich ist `docs/ARCHITECTURE.md` 2 und 7. Zusammengefasst [verifiziert]:

- SOOD über Multicast 239.255.90.90:9003; Broadcast bekam keine Antwort. Antworten nach `unique_id` deduplizieren. Der WebSocket-Port ist `http_port` aus der Antwort; er ist nicht fest [geprüft, Roon-Mitarbeiter im Community-Forum] und wird nie fest codiert.
- MOO über `ws://<host>:<http_port>/api`, binäre Frames. Header-Reihenfolge beliebig. Leere Frames ignorieren (sie gehen einer Verdrängung voraus).
- `register` bleibt vor der Freigabe unbeantwortet; danach `CONTINUE Registered` mit Token. Der Core pingt alle 2 s, Antwort `COMPLETE Success`.
- Titelwechsel: `zones_changed` mit `state: loading` ohne `seek_position`, dann `playing`. Pause/Fortsetzen als einzelne `zones_changed`. Gruppen bekommen eine neue `zone_id`, behalten aber die Output-IDs.
- Cover und Künstlerbilder per HTTP ohne Token: `/api/image/<key>?scale=…&width=…&height=…`; Künstlerbilder über `now_playing.artist_image_keys`, ohne Browse-API (Browse würde eine erneute Freigabe auslösen).
- Eine gestoppte Zone ohne Warteschlange kann eine Extension nicht starten.

### 4.2 Plattformneutrale Fachspezifikation `docs/SPEC.md` (Phase F3)

Aus der fertigen Fire-TV-Version abgeleitet, als Vorgabe für den Web-Kern:

- Bildschirmzustände und Übergänge (Verbinden, Hinweis nach 20 s, manuelle Eingabe, Freigabe, Zonenliste, fehlende Zone, Now Playing, Liedtexte, Einstellungen) und die Bedeutung der Zurück-Taste.
- Verbindungsreihenfolge (gespeicherter Endpunkt per Kurzprobe → Discovery → manuell), Reconnect mit Backoff, Watchdog bei Funkstille, Rediscovery nach wiederholten Fehlschlägen, verzögertes Trennen beim Verlassen der App.
- Zonenauswahl inklusive Gruppenverfolgung über Output-IDs.
- Fortschrittsinterpolation (Regeln aus `ARCHITECTURE.md` 8.4).
- Steuerung: nur senden, wenn `is_*_allowed`; optimistischer Wechsel der Play/Pause-Anzeige.
- Steuerleiste: Reihenfolge, Fokus, Ausblenden.
- Einstellungen mit Werten und Standardwerten (Abschnitt 6.7).
- Liedtexte: Query-Varianten, Umgang mit 404/503/429/Zeitüberschreitung, Cache-Regeln (gefunden unbegrenzt, nicht gefunden 7 Tage, nicht erreichbar nur 10 min im Speicher, höchstens 300 Dateien), Vorlauf 250 ms, `[offset:]`-Behandlung, Timing-Einstellung, Darstellung synchron/unsynchron/keine.
- Bildschirm-an-Verhalten.
- Die Plattform-Schnittstelle aus 4.4 als eigener Abschnitt: was jede Plattform liefern muss und wie der Kern reagiert, wenn eine Fähigkeit fehlt (z. B. Discovery „nicht verfügbar“).

### 4.3 Entwicklerwerkzeug `roon-cli`

Das vorhandene Kotlin-CLI ist das einzige Test- und Aufzeichnungswerkzeug für alle Plattformen (kein Node-Werkzeug). Es steuert in End-to-End-Tests die Gegenseite (nur `$ROON_TEST_ZONE`), zeichnet MOO-Nachrichten auf, misst Seek-Raten und fragt LRCLIB lesend ab.

### 4.4 Code-Teilung und Web-Kern

**Zwei Implementierungen:**

- **Kotlin** (vorhanden): Fire OS Version 1.
- **Web-Kern in TypeScript** (`web/`): Tizen, Vega und optional später Fire OS.

Geteilt zwischen beiden: Spezifikation, Fixtures, Testvektoren, Design-Tokens und Texte. Jede reine Logik (Fortschritt, Zonen-Resolver, LRC-Parser, Sync, Query-Varianten, Cache-Entscheidungen) wird auf beiden Seiten gegen dieselben JSON-Fälle geprüft.

**Aufbau des Web-Kerns:** Der Kern läuft in jeder Hülle unverändert. Alles Plattformspezifische steckt hinter einer schmalen Schnittstelle `Platform` in `web/platform/`:

| Bereich | Aufgabe der Plattform |
|---|---|
| Discovery | SOOD ausführen, wenn die Plattform es kann, und Host/Port liefern; sonst „nicht verfügbar“ melden (dann D3/D4, 6.2) |
| Speicher | persistenter Schlüssel-Wert-Speicher (Installationskennung, Token, Endpunkt, Zone, Einstellungen) und Liedtext-Cache |
| Tasten | Plattform-Tastencodes auf Aktionen abbilden (Navigation, OK, Zurück, Media-Tasten); Media-Tasten registrieren |
| Bildschirm | Bildschirmschoner/Standby nach Einstellung unterdrücken |
| Lebenszyklus | Vordergrund/Hintergrund melden, App verlassen |
| Netzwerkstatus | Verbindung bestätigt / verloren |
| Geräteinfo | Gerätename für den Extension-Anzeigenamen, Plattform, Version |
| Log | Ausgabe mit Präfix `Roomscreen` an die Debug-Kette der Plattform |

Regeln:

- `web/core` und `web/ui` importieren keine Plattform-APIs (`tizen.*`, `webapis.*`, Vega-Bridge, Android-Bridge). Per Lint-Regel erzwungen.
- Die Hüllen (`tizen/`, `vega/`) enthalten nur die Implementierung der Schnittstelle, Paketierung und gegebenenfalls nativen Code (Tizen: WebAssembly; Vega: C++-Turbo-Module).
- `web/platform/` enthält eine Desktop-Implementierung (Discovery „nicht verfügbar“, Speicher im Arbeitsspeicher oder `localStorage`), mit der der Kern im Desktop-Chromium und in Playwright läuft.
- Kotlin Multiplatform nur prüfen, wenn sich in Phase T2 zeigt, dass node-roon-api im Web-Kern nicht brauchbar ist; dann zuerst eine eigene schlanke MOO-Implementierung in TypeScript erwägen.

## 5. Teil A – Fire TV / Android (Fire OS)

### 5.1 Technik

Kotlin, Compose for TV, MVVM, Coroutines/Flow, DataStore, OkHttp, Coil 3 (Speicher-Cache 16 MB), Gradle → APK. compileSdk 37, targetSdk 36 (aktuelle AndroidX/Coil/OkHttp-AARs verlangen compileSdk 37, `ARCHITECTURE.md` 7).

### 5.2 Echo Show 15 (Phase F5)

Kein ADB, kein Sideloading; Verteilung über **Live App Testing (LAT)** an selbst definierte Tester.

- Verstecktes Diagnose-Overlay (Verbindungsstatus, Core-Adresse, Discovery-Ergebnis, letzte Fehler), weil es dort keine Logs gibt. Dasselbe Overlay auch im Web-Kern.
- Touch primär; alle Funktionen per Fernbedienung und Touch erreichbar.
- LAT-Pflichtangaben: Titel, Kategorie, Icons (512×512 und 1280×720 px), Sprachen, Content-Rating, Export-Compliance.
- Datenschutz: außer Token und Einstellungen nichts speichern oder übertragen; an LRCLIB nur Titel-Metadaten (steht in der README).
- Signiert mit dem Release-Schlüssel aus Phase F3.
- Zu prüfen: Laufzeit einer LAT-Testphase, Freigabedauer, unbefristete Nutzbarkeit, Echo Show 15 in der Geräteauswahl.

### 5.3 Veröffentlichung im Amazon Appstore (Option, Entscheidung 4)

Technisch: neue App im Developer-Portal anlegen, APK oder AAB hochladen, Metadaten und Content-Rating ausfüllen [geprüft]. Pflicht sind ein Icon 512×512 (PNG mit Transparenz) und 3 bis 10 Screenshots [geprüft, Test Criteria 3.15]. Vega-Pakete (`.vpkg`) können unter demselben Eintrag eingereicht werden [geprüft, Amazon Vega-Einreichung].

Hürden für Roomscreen [alle geprüft, Amazon Test Criteria und IP-Richtlinie]:

1. **Marke „Roon“:** Erlaubnis von Roon Labs nötig, auch für die Beschreibung; Disclaimer reichen nicht. Vor jedem Einreichen schriftlich anfragen (mache ich).
2. **Kernfunktion ohne Core:** Prüfer bewerten, ob die App ihren Hauptzweck auf dem Gerät erfüllt; ist die einzige Kernfunktion nicht verfügbar, fällt die App durch, auch mit sauberer Fehlermeldung (Test 3.3). Prüfer haben keinen Roon Core. Vorschlag: ein Demo-Modus, der die Fixtures abspielt und ausdrücklich als Demo gekennzeichnet ist. Ob Amazon dafür ein Feld für Prüfhinweise anbietet **[zu prüfen]**.
3. **Ladehinweis:** Dauert Laden länger als 15 s, muss die App das anzeigen (Test 2.3). Der Verbindungsbildschirm braucht von Beginn an einen sichtbaren Ladezustand; der ausführliche Hinweis nach 20 s bleibt zusätzlich.
4. **Media-Tasten:** In Nicht-Medien-Apps dürfen sie keine andere Funktion haben (Test 4.6). Roomscreen nutzt sie zur Steuerung der Roon-Wiedergabe; ob ein Prüfer das als Medienwiedergabe wertet, ist offen.
5. **Bildschirm an:** Test 3.14 nennt `FLAG_KEEP_SCREEN_ON` für Video-Apps während der Wiedergabe; Roomscreen ist keine Video-App. Verhalten im Prüfprozess **[zu prüfen]**.
6. Weitere Prüfpunkte, die Roomscreen erfüllen muss und die als Teil der Abnahme geprüft werden: Bildschirmfüllung ≥ 90 %, Fokus sichtbar, keine Sackgassen in der Navigation, Beenden über Home in höchstens 2 s, Daten bleiben nach Update erhalten.

Erster Schritt vor einer öffentlichen Veröffentlichung ist LAT (wie 5.2), um die Prüfpraxis ohne Öffentlichkeit zu sehen. Kosten des Entwicklerkontos **[zu prüfen]**.

## 6. Teil B – Web-Kern: Samsung Tizen (ab T0) und Fire TV Vega (ab V0)

### 6.1 Technik

| Komponente | Vorgabe |
|---|---|
| Sprache | TypeScript |
| UI | schlank: Vanilla TS oder eine kleine Bibliothek (z. B. Preact). React nur mit Begründung (Speicher, Startzeit). |
| Build | Vite oder vergleichbar; Ausgabeziel ist die älteste Web-Engine aller Hüllen: Chromium M85 (Tizen 6.5), es sei denn, Vega WebView ist älter **[zu prüfen in V0]**. Bundle auf nicht unterstützte Syntax prüfen. |
| Paket | Tizen: `.wgt`, gebaut und signiert über Tizen-CLI bzw. Samsung-Skills (6.5). Vega: `.vpkg` über Vega CLI (6.10). |
| Tests | Vitest (Logik gegen `shared/testvectors/`), Playwright (UI am Desktop und auf den Geräten über CDP) |
| Schrift | dieselben Schriften wie Android, im Paket enthalten (keine Web-Fonts aus dem Netz) |

Verfügbare Web-APIs und CSS-Funktionen gegen die Samsung-Tabelle „Web Engine Specifications“ und die Angaben zu Vega WebView prüfen, nicht gegen den Desktop-Chrome. Die Einschränkungen, die Sonance 3 für Chromium 63 auflistet [geprüft], gelten für M85 nicht automatisch.

### 6.2 Roon-Anbindung im Web-Kern (am Beispiel Tizen)

**Bibliothek:** node-roon-api läuft laut eigener README auch im Browser, gebündelt mit der App; statt `start_discovery()` wird `ws_connect({ host, port, onclose })` verwendet [geprüft]. Prüfen:

1. Lässt sich node-roon-api samt Transport-Service für das Ausgabeziel bündeln? Image-Service entfällt (Bilder per HTTP).
2. Wo speichert die Bibliothek im Browser Konfiguration und Token (`lib.js`)? Speicherung auf die Plattform-Schnittstelle umlenken, damit Token einen Neustart von App und Gerät überleben **[zu prüfen]**.
3. Akzeptiert der Core die WebSocket-Verbindung aus der Tizen-App (Origin)? **[zu prüfen, auf dem Fernseher]**
4. Laden Cover und Künstlerbilder per HTTP-URL als `<img>` (CSP, `access`)? **[zu prüfen, auf dem Fernseher]**
5. **`extension_id` pro Installation** wie auf Android (`io.github.knoellp.roomscreen.<installId>`), einmal erzeugt und dauerhaft gespeichert. Anzeigename „Roomscreen (<Gerätename>)“ über die Plattform-Schnittstelle; ist kein Name verfügbar, „Roomscreen (Samsung TV)“ bzw. „Roomscreen (Fire TV)“. Leere Frames ignorieren.
6. Liefert node-roon-api die Ereignisse so, dass Watchdog, Rediscovery und verzögertes Trennen wie in `SPEC.md` umsetzbar sind, oder muss eine dünne eigene Schicht darüber? **[zu prüfen]**

**Discovery: das Kernrisiko.** Web-Code hat keinen UDP-Zugriff; Discovery ist deshalb Aufgabe der Plattformschicht. Optionen für Tizen in dieser Reihenfolge:

| Option | Stand |
|---|---|
| **D1: WebAssembly + Tizen Sockets Extension** | TCP und UDP für WebAssembly-Apps inklusive IPv4-Multicast-Optionen; nicht im Hauptthread (Worker bzw. Emscripten `PROXY_TO_PTHREAD`/`--proxy-to-worker`); WebAssembly ab Tizen 5.5 [alles geprüft, Samsung-Doku]. Privilegien, Zertifikatsstufe (Public/Partner) und Multicast-Empfang auf dem Frame **[zu prüfen]**. Braucht das Samsung Emscripten SDK; ein kleiner SOOD-Client in C genügt. |
| **D2: Unicast-SOOD an bekannte Host-IP** | Falls Multicast scheitert. Ob der Core auf Unicast antwortet: **[zu prüfen]**, zuerst von der VM mit `scripts/sood-probe.py`. |
| **D3: Host-Eingabe + Port-Suche** | Nur Host eingeben, Ports per WebSocket mit `registry:1/info` probieren. Bereich nicht offiziell dokumentiert; nur Komfortfunktion. |
| **D4: Manuelle Eingabe von Host und Port** | Immer vorhanden, mit Verbindungstest. Wo Roon dem Benutzer den Port anzeigt **[zu prüfen]**. |

Vorgabe: D4 ist Pflicht und von Anfang an vollwertig; D2–D4 liegen im Web-Kern und gelten damit für alle Hüllen. D1 wird in Phase T2 als Spike geprüft; ist der Aufwand unverhältnismäßig oder fehlen Privilegien, wird D3+D4 die Lösung. Wie auf Android gilt: gespeicherter Endpunkt zuerst. Weil der Port aus derselben SOOD-Antwort stammt, die auch die Fire-TV-App nutzt, ist er im Normalfall stabil; ändert er sich, muss die Ersteinrichtung das verständlich machen.

### 6.3 Netzwerk, Berechtigungen, Speicherung (Tizen)

- `config.xml`: Privileg `http://tizen.org/privilege/internet` und `access`-Einträge [geprüft, Samsung FAQ]. Wildcard für einen Core mit wechselnder LAN-IP und Wirkung von `access` auf WebSocket und HTTP-Bilder **[zu prüfen]**.
- Content Security Policy so eng wie möglich: eigene Dateien, `ws:` und `http:` zum LAN-Core, `https://lrclib.net`.
- LRCLIB: Browser können den `User-Agent` nicht setzen; LRCLIB akzeptiert dafür `Lrclib-Client` mit App-Name, Version und Projekt-URL [geprüft, LRCLIB-Doku]. CORS-Preflight mit diesem Header und Verhalten der Tizen-Laufzeit **[zu prüfen]** (`curl -X OPTIONS` von der VM, dann auf dem Fernseher).
- Netzwerkstatus überwachen; nach dem Wiedereinschalten ist das Netz laut Samsung nicht sofort verfügbar [geprüft, Samsung FAQ]. Reconnect erst nach bestätigter Verbindung.
- Gespeichert werden nur: Installationskennung, Token, Host:Port, Zone (mit Output-IDs), Einstellungen, Liedtext-Cache mit denselben Regeln wie Android (4.2). Speicherort nach Prüfung der Persistenz über Neustarts wählen; Klartextspeicherung des Tokens in der README benennen.

### 6.4 Fernbedienung und Bildschirm (Tizen)

- Media-Tasten (Play/Pause, Next, Previous, Rewind, Fast Forward) auf Tizen registrieren; API und Privileg laut Samsung-Doku „Remote Control“ **[zu prüfen]**. Belegung wie Android (`ARCHITECTURE.md` 8.2).
- Zurück-Taste nach Samsung-Vorgaben, Verhalten sonst wie Android (schließt Einstellungen und Zonenliste, sonst App verlassen) **[zu prüfen]**.
- Bildschirmschoner unterdrücken (Samsung-Guide „Setting Screensaver“), mit derselben Einstellung „immer / nur während Wiedergabe“.
- Safe Area / Overscan; Layout bei 1920×1080 CSS-Pixeln und auf dem 4K-Panel prüfen.
- Hintergrund wie Android einmal pro Titel vorberechnen (Canvas: Zuschnitt 16:9, verkleinern, Box-Blur, hochskalieren), nicht per Live-`filter: blur()`; ob Live-Blur ausreichend schnell wäre, darf gemessen werden, ist aber nicht Vorgabe. Höchstens drei vorberechnete Hintergründe vorhalten, Object-URLs und Canvas-Speicher freigeben. Das gilt im Web-Kern für alle Hüllen.

### 6.5 Entwicklungsumgebung und Debug-Kette (Tizen)

**Samsung tizen-agent-skills** (Apache-2.0) [geprüft, Repository]:
- Installation für Claude Code: Repository klonen, `bash tizen-sdk-skills/claude/setup/setup.sh`, ausgegebenen Hook-Block in `settings.json` übernehmen, Sitzung neu starten. Node.js 20+. Das SDK installieren die Skills selbst.
- Relevante Skills (`tizen-<befehl>`): `sdk-install`, `tv-sdk-install`, `certificate-manager`, `remote-device`, `create-project`, `build-project`, `install-app`, `webapp-debug`, `playwright-test`, `screenshot`, `dlog-analyzer`, `sdb-helper`.
- `certificate-manager` kennt Samsung-Account-Login, DUID-Abfrage und Samsung-Distributor-Zertifikat (Public/Partner). Den Account-Login mache ich, wenn er interaktiv ist.
- `webapp-debug` liefert einen CDP-Endpunkt; `playwright-test` hängt sich per `connectOverCDP` an die laufende App.
- `screenshot` auf einem Seriengerät im Entwicklermodus **[zu prüfen]**; Fallback `page.screenshot()` über CDP (nur App-Inhalt).
- Die Skills sind neu (Version 1.x, 2026). Scheitert ein Skill am Frame, auf `tizen`/`sdb` ausweichen und das Problem dokumentieren.

**Apps2Samsung** (MIT) [geprüft, Repository]: findet Fernseher, verwaltet Samsung-Zertifikate, installiert `.wgt`, startet/stoppt/debuggt Apps, zeigt DUID, Tizen-Version, Modell, Entwicklermodus-Status, Live-Log, Fernbedienungsfunktion über Samsungs Fernbedienungskanal. Rolle: Diagnose und Rückfallweg. Die Komponenten `TizenAppDebugger.cs`, `DevToolsConsole.cs`, `DevToolsInspector.cs` sowie die Nutzbarkeit des Fernbedienungskanals von der VM **[zu prüfen]**.

**Entwicklermodus:** auf dem Fernseher aktiv, mit der VM-IP (192.168.1.198 laut `ARCHITECTURE.md` 3) als Host. Haltbarkeit von Entwickler-Apps und Zertifikat über Neustart und Firmware-Update **[zu prüfen]**, in der README dokumentieren.

### 6.6 Referenzprojekte und Lizenzen

| Projekt | Lizenz | Nutzung |
|---|---|---|
| node-roon-api | Apache-2.0 | Laufzeitbibliothek im Web-Kern |
| tizen-agent-skills | Apache-2.0 | Entwicklungswerkzeug |
| Apps2Samsung | MIT | Entwicklungswerkzeug |
| roon-rs | MIT/Apache-2.0 | Protokolldoku |
| Comfortaa | SIL OFL 1.1 | Schrift (falls der „via roon“-Hinweis so bleibt, siehe 14) |
| Vega SDK und Bibliotheken (`@amazon-devices/*`) | Lizenz **[zu prüfen]** | Vega-Hülle |
| **Sonance 3** | **GPL-3.0** | **Kein Code übernehmen**; nur Anschauung für Fokusführung. GPL-3.0 und die Zusatzbeschränkungen der README sind mit Apache-2.0 für übernommenen Code nicht vereinbar [geprüft]. |

### 6.7 Funktionsumfang Web-Kern Version 1 (Parität mit Fire TV, gilt für Tizen und Vega)

| Bereich | Umfang |
|---|---|
| Verbindung | gespeicherter Endpunkt zuerst, Discovery-Lösung der jeweiligen Plattform, manuelle Eingabe, Hinweis nach 20 s, Freigabehinweis, Token, Reconnect wie `SPEC.md` |
| Zonen | Liste, gespeicherte Zone, Wechsel über Steuerleiste, Gruppenverfolgung, Hinweis bei fehlender Zone |
| Now Playing | Cover, Hintergrund, Infoleiste, interpolierter Fortschritt, Platzhalter |
| Steuerung | Media-Tasten, Steuerleiste (Zonen, Einstellungen, Liedtexte, zurück, Play/Pause, weiter), Ausblenden nach 5 s |
| Einstellungen | Hintergrund (Cover/Künstlerbild/Farbe), Schärfe, Hintergrundfarbe (sechs Werte wie Android), Steuerleiste, Bildschirm an, „via roon“ (je nach Entscheidung 14), Liedtexte (LRCLIB), Liedtext-Timing −3,0…+3,0 s, Core neu suchen, Zone wechseln |
| Liedtexte | wie Android (4.2), gegen dieselben Testvektoren |
| Diagnose | verstecktes Overlay wie 5.2 |
| Sprache | Englisch und Deutsch aus `shared/strings/` |

Was auf einer Plattform nicht sinnvoll oder nicht machbar ist, wird in `docs/ARCHITECTURE.md` begründet und in der README als Abweichung genannt.

### 6.8 Vega: Plattform und App-Typ

- **Geräte:** Fire TV Stick 4K Select (2025) und Fire TV Stick HD (2026) laufen mit Vega OS; Android-Apps laufen dort nicht [geprüft, Amazon]. Für Endkunden sind auf Vega nur Apps aus dem Amazon Appstore installierbar [geprüft, Amazon-Produktseite laut Fachpresse].
- **App-Typ:** React-Native-for-Vega-App, deren einzige Oberfläche eine Vega WebView mit dem gebündelten Web-Kern ist. Vega WebView ist ein offizieller Weg für Web-Apps; Nachrichten laufen von der Hülle in die Webseite über `injectJavaScript` und zurück über `window.ReactNativeWebView.postMessage` / `onMessage` [geprüft, Amazon Vega WebView-Doku]. Ob die WebView gebündelte Dateien aus dem Paket laden kann (statt einer URL) und mit welcher Engine-Version sie läuft **[zu prüfen in V0]**. Eine gehostete URL ist nach Abschnitt 1 ausgeschlossen.
- **Nativer Code:** als C++-Turbo-Module möglich [geprüft, Amazon Vega-Doku]; nur für Discovery und gegebenenfalls Speicher vorgesehen.
- **Plattformschicht:** in TypeScript in der React-Native-Hülle; sie implementiert `Platform` (4.4) und reicht Aufrufe über die Nachrichtenbrücke an den Web-Kern weiter. Die Brücke bekommt ein kleines, versioniertes Nachrichtenformat mit eigenen Tests.
- **Amazon App Porting** (KI-gestützte Umwandlung von Fire-OS-Apps in Vega-Apps) [geprüft] wird für die Kotlin-App nicht verwendet, weil der Web-Kern ohnehin entsteht.

### 6.9 Vega: Roon-Anbindung und Discovery

- WebSocket und HTTP-Bilder aus der Vega WebView zum LAN-Core (CSP, gemischte Inhalte, Origin) **[zu prüfen]**; ebenso CORS bei LRCLIB mit `Lrclib-Client`.
- **Discovery:**
  - **V-D1: C++-Turbo-Module mit UDP-Multicast-SOOD.** Ob Vega Apps UDP und Multicast-Empfang erlaubt, steht in der gesichteten Amazon-Doku nicht **[zu prüfen]**. Ein Entwicklerbericht im Amazon-Forum zeigt UDP-Broadcast aus einer Vega-App und beschreibt, dass der Fire TV Stick HD (2026) im WLAN-Stromsparmodus Broadcast und Multicast teils über 30 s nicht empfängt [geprüft, nur Entwicklerbericht, nicht von Amazon bestätigt]. SOOD-Wiederholungen deshalb über mindestens 60 s verteilen.
  - Rückfall D2–D4 aus dem Web-Kern wie bei Tizen.
  - Gespeicherter Endpunkt zuerst; auf Vega besonders wichtig.
- **Speicher:** Persistenz der WebView-Speicher über App- und Geräteneustart **[zu prüfen]**; falls unsicher, Speicher über die Brücke in der Hülle halten.
- `extension_id` pro Installation wie auf allen Plattformen.

### 6.10 Vega: Fernbedienung, Bildschirm, Entwicklung und Debug

- **Tasten:** Die Vega-Doku beschreibt, wie Zurück-Tasten-Ereignisse im Web-JavaScript ankommen [geprüft]. Ob Media-Tasten (Play/Pause, Weiter, Zurück) die App erreichen, ohne dass sie selbst Medien abspielt, und ob dafür `VegaMediaControl` nötig ist **[zu prüfen]**.
- **Bildschirm an / Bildschirmschoner** auf Vega **[zu prüfen]**.
- **Entwicklung:** Vega SDK und CLI; Vega Virtual Device für schnelle Läufe; echtes Gerät im Entwicklermodus, der über das Amazon-Entwicklerkonto registriert wird; Installation und Start über die Vega CLI [geprüft, Amazon Vega-Doku]. Die Registrierung im Entwicklerkonto mache ich, wenn sie interaktiv ist.
- **Debug:** Die WebView lässt sich im Debug-Build mit Chrome DevTools untersuchen [geprüft]; Playwright per CDP **[zu prüfen]**. Automatisierte Eingaben über `inputd-cli` im Entwicklermodus [geprüft, dass es existiert; Eignung für Tastendrücke zu prüfen]. Screenshot-Weg **[zu prüfen]**.
- **Verteilung an andere:** nur über den Appstore bzw. LAT; ob LAT für Vega-Pakete funktioniert **[zu prüfen]**.

### 6.11 Funktionsumfang Vega Version 1

Wie 6.7. Abweichungen (z. B. fehlende Media-Tasten oder Discovery nur manuell) in `docs/ARCHITECTURE.md` begründen und in der README nennen.

## 7. Oberfläche (alle Plattformen)

Maßgeblich ist der heutige Fire-TV-Stand (Roborazzi-Referenzbilder in `app/src/test/screenshots/`): Cover unten links, rechts daneben Zone/Status, Titel, Künstler, Album, darunter Fortschrittsbalken mit Zeiten; Hintergrund aus Cover oder Künstlerbild mit dunklem Verlauf; Liedtext-Ansicht mit kleinem Cover links und zentrierter aktiver Zeile rechts. Erscheinungsjahr nur, wenn Roon es liefert (im aufgezeichneten Zonenmodell nicht gefunden; nicht nachrüsten, solange keine Quelle ohne Browse-API existiert).

Bedienung vollständig per Steuerkreuz, OK, Zurück und Media-Tasten; auf dem Echo Show zusätzlich Touch. Bedienelemente erscheinen bei Eingabe und verschwinden nach 5 s (oder bleiben laut Einstellung sichtbar). Letzte Ansicht (Cover/Liedtexte) wird gespeichert.

Alle Plattformen sollen gleich aussehen. In Phase F3 werden Farben, Abstände, Schriftgrößen und die Hintergrundfarben aus dem Android-Theme nach `shared/design/` extrahiert. Abnahme durch Gegenüberstellung von Screenshots (Android, Tizen, Vega) mit denselben Fixture-Daten.

## 8. Synchronisierte Liedtexte (alle Plattformen)

Umgesetzt auf Android nach `ARCHITECTURE.md` 9 [verifiziert]; der Web-Kern übernimmt das Verhalten:

- `GET /api/get` mit Titel, Künstler, Album (weggelassen, wenn leer), Dauer in ganzen Sekunden (weggelassen bei 0). Mit Dauer nur Treffer mit höchstens ±2 s Abweichung [geprüft].
- Query-Varianten kumulativ: exakt → Titel ohne Versionszusätze („(Album Version)“, Remaster, Edit, Mix …; „(Live)“ bleibt) → erster Künstler → ohne Album. Gleiche Varianten fallen weg. Sind alle 404: `/api/search` mit der letzten Variante, Treffer selbst auf ±2 s filtern, synchron vor unsynchron vor instrumental.
- 503/429/I/O-Fehler/Zeitüberschreitung: zweimal wiederholen (1 s, 3 s), dann „nicht erreichbar“; nie als „keine Texte“ anzeigen, nie auf Platte cachen, Suche mit weiteren Varianten sofort abbrechen.
- Kennung: Android `User-Agent: Roomscreen/<version> (https://github.com/knoellp/Roomscreen)`, Web-Kern derselbe Text im Header `Lrclib-Client`.
- Cache: eine Datei bzw. ein Eintrag pro Titel-Schlüssel, höchstens 300; gefunden unbegrenzt, nicht gefunden 7 Tage, nicht erreichbar 10 min nur im Speicher; defekte Einträge löschen.
- Synchronisation: aktive Zeile = letzte Zeile mit `Zeitstempel ≤ Position + 250 ms` (plus Timing-Einstellung); `[offset:]` als `Zeit − offset`. Pause hält an; nach Seek sofort die richtige Zeile. Abfrage wird bei Titelwechsel abgebrochen.
- Keine Anfrage, solange der Liedtext-Schalter aus ist; Einstellung „Lyrics (LRCLIB)“ aus = gar keine Anfragen und kein Schalter.

## 9. Zonenauswahl und Stabilität

### Zonenauswahl
- Auswahl lokal speichern (Zone und deren Output-IDs) und beim nächsten Start verwenden. Wechsel jederzeit.
- Auswahl darf niemals eine Wiedergabe starten oder andere Zonen verändern.
- Gruppen: Wird die gewählte Zone gruppiert, zeigt die App die Gruppe; löst sie sich auf, zurück zur Zone. Die gespeicherte Auswahl wird nie automatisch gelöscht; passt keine Zone, erscheint ein Hinweis.

### Stabilität
Die App läuft stundenlang ohne Interaktion. Abzudecken: Verbindungsabbruch, Core-Neustart, WLAN-Unterbrechung, Fernseher bzw. Stick aus/an; Zonenwechsel, leere Zonen, fehlende Cover; Lifecycle ohne Doppelverbindungen (Android: verzögertes Trennen nach 2 s; Tizen: Multitasking laut Samsung-Guide; Vega: Lebenszyklus der Hülle **[zu prüfen]**); kein Speicherwachstum über viele Titelwechsel; ressourcenschonend für den Fire TV Stick HD (1 GB RAM), den Vega-Stick und den Frame.

## 10. Phasen

### Erledigt (Fire TV)
v1-Phasen 1 (Roon-Verbindung), 2 (TV-Oberfläche), 3 (Liedtexte) und 4 (Stabilität), F3 (Abnahme, Release-Signierung, Übergabe; `ARCHITECTURE.md` Kapitel 11) und F3b (Release-Pipeline, Diagnose-Overlay, Touch, LAT-Vorbereitung; `ARCHITECTURE.md` Kapitel 12, Stand `STATUS.md`). Nachweise wie in Abschnitt 3.

### Teil A – Fire TV (Fire OS) abschließen

**F3 – Abnahme Fire TV Version 1, Release-Signierung, Übergabe** (erledigt 2026-10-19)
1. **Entscheidungen einholen** (Abschnitt 14): Speicher-Toleranz im Liedtext-Modus, „via roon“-Hinweis, Release-Schlüssel.
2. **Release-Signierung:** eigenen Release-Keystore anlegen (außerhalb des Repos, Pfad und Passwörter in `config/local.env` oder einer eigenen gitignored Datei), `signingConfig` für `release`, Build einer Release-APK. Der Wechsel vom Debug- auf den Release-Schlüssel erfordert auf dem Fire TV **einmal** Deinstallation und neues „Enable“ in Roon; vorher mich fragen. Danach nur noch mit dem Release-Schlüssel installieren.
3. **Restpunkte auf dem Gerät nachweisen:**
   - Reconnect nach Neustart des echten Roon Cores (Neustart löse ich aus, wenn Claude Code Bescheid gibt; Log mit Zeitstempeln bis zur erneuten Anzeige).
   - Manuelle Host/Port-Eingabe auf dem Gerät (Maestro-Flow mit gelöschter Endpunkt-Erinnerung über den Debug-Hook, nicht über App-Daten-Löschen).
   - „Bildschirm an: nur während Wiedergabe“ per `dumpsys power` bei Wiedergabe und bei Pause.
   - Ruckeln der Liedtext-Ansicht und des scharfen Hintergrunds per `dumpsys gfxinfo`, gleiche Methode wie `ARCHITECTURE.md` 8.5.
   - Zwei Instanzen: App auf dem Fire TV und `roon-cli watch` mit eigener `extension_id` gleichzeitig über mindestens 30 min, keine Verdrängung im Log.
   - Physische Fernbedienung: Ich drücke nach Ansage Play/Pause, Weiter, Zurück; Claude Code prüft im Log, welche Tastencodes ankommen.
   - Ladezustand: Der Verbindungsbildschirm zeigt sofort einen sichtbaren Ladezustand, nicht erst nach 20 s (Vorbereitung für 5.3, Punkt 3).
4. **Speicher:** je nach Entscheidung (14.1) Maßnahme umsetzen und erneut messen.
5. **Abnahmekriterien Fire TV Version 1** (Abschnitt 13) als ein Maestro-Gesamtflow, danach meine manuelle Abnahme.
6. **Übergabe an den Web-Kern:**
   - `docs/SPEC.md` (4.2) inklusive Plattform-Schnittstelle.
   - Fixtures nach `shared/fixtures/` verschieben; Kotlin-Tests weiter grün.
   - Testvektoren nach `shared/testvectors/` als JSON: Fortschritt (aus `ProgressInterpolatorTest`), Zonen-Resolver/Gruppen, LRC-Parser, Sync inkl. Offset und Timing, Query-Varianten, LRCLIB-Antworten (200/404/503) und erwartete Ergebnisse. Die Kotlin-Tests lesen diese Dateien.
   - Design-Tokens nach `shared/design/`, Texte nach `shared/strings/`; Android-Ressourcen werden daraus erzeugt oder gegen sie geprüft (Test, der Abweichungen meldet).
   - README auf Stand Fire TV Version 1, mit dem Hinweis, dass Geräte mit Vega OS nicht unterstützt werden.

**F3b – Release-Pipeline, Touch, Diagnose-Overlay, LAT-Vorbereitung** (Auftrag `PLAN-F3b.md`; Umsetzung 2026-10-20/21, Nachweise `ARCHITECTURE.md` Kapitel 12)
1. GitHub-Actions-Workflow (Release-Ereignis und Probelauf), signiert mit dem Schlüssel aus den Secrets, Prüfung von Keystore, Zertifikat, Version und Changelog, Assets plus `releases/` im Repo (aktuelle und fünf Vorversionen), `CHANGELOG.md`.
2. Release v1.0.0 (versionCode 2) und v1.1.0 (versionCode 3, Touch und Overlay), Installation ohne ADB über Downloader und die stabile Adresse, Update-Test ohne neues Enable.
3. Touch: Tipp zeigt die Leiste, alle Knöpfe und Zeilen tippbar, Schließen-Zeilen in Zonenliste und Einstellungen, Trefferflächen mindestens 48 dp; Maestro-Touch-Flows und Regression auf dem AFTSS.
4. Diagnose-Overlay (Build, Gerät, Verbindung, letzte 20 Eingaben, 200 Logzeilen), geöffnet per fünfmal Tippen unten rechts, Einstellungszeile oder Debug-Broadcast; kein HTTP-Export (Entscheidung 8.3: später).
5. Manifest-Test, Roborazzi in tvdpi und hdpi, Konfigurationswechsel-Test, Lebenszyklus-Test, kein Audio-Fokus.
6. `docs/store/`: Metadaten en/de, Datenschutz-Entwurf, Kriterien-Checkliste, Icons; Screenshots folgen mit dem Nutzer.

### Teil B – Web-Kern und Samsung Tizen

**T0 – Vorbereitung**
Vor T1 von mir abfragen bzw. bestätigen lassen: IP des Frame (feste IP richte ich ein), Tizen-Version und Modellnummer (z. B. über Apps2Samsung), Entwicklermodus aktiv mit VM-IP als Host, Samsung-Account für das Zertifikat. `FRAME_IP` in `config/local.env.example` ergänzen. Von der VM prüfen: Unicast-SOOD (D2) und LRCLIB-CORS-Preflight.

**T1 – Entwicklungs- und Debug-Kette (höchste Priorität in Teil B)**
Skills, SDK, TV-Erweiterung, Samsung-Zertifikat, `sdb` zum Frame, minimale Test-App bauen, signieren, installieren, starten, CDP. **Nachweis:** echte Konsolenausgabe und ein absichtlich ausgelöster JS-Fehler, vom Fernseher ausgelesen. Außerdem Screenshot-Weg und automatisierte Tastendrücke prüfen. Ergebnis und Grenzen in `docs/ARCHITECTURE.md`.

**T2 – Web-Kern-Gerüst und Roon-Spike auf dem Fernseher**
`web/` mit `core`, `ui`, `platform` anlegen, Lint-Regel gegen Plattform-APIs außerhalb der Hüllen, Desktop-Implementierung von `Platform`. Dann auf dem Frame: node-roon-api gebündelt, `ws_connect` zum Core mit eigener `extension_id`, Freigabe, Token-Persistenz über App- und Fernseher-Neustart, Zonen, Titelwechsel als Text, Bild-URL als `<img>`. Discovery-Spike D1, Entscheidung D1 oder D3+D4. Ist die direkte Verbindung nicht stabil herstellbar: Problem und Alternativen dokumentieren und mich fragen, bevor irgendeine zusätzliche Serverkomponente eingeführt wird.

**T3 – Oberfläche** nach `SPEC.md` und `shared/design/`: Now Playing, Hintergründe, Ersteinrichtung, Zonen, Einstellungen, Fehlerzustände, Diagnose-Overlay. Alles in `web/ui`; Tizen-spezifisches nur in `tizen/`. Screenshot-Gegenüberstellung mit Android.

**T4 – Liedtexte** nach `SPEC.md` in `web/core`, gegen die Testvektoren aus F3.

**T5 – Steuerung, Stabilität, Abnahme Tizen**
Media-Tasten, Bildschirmschoner, Reconnect, Langzeittest (mehrere Stunden mit wechselnden Titeln, Core-Neustart, Netzunterbrechung), Speicherverlauf. Fire TV und Frame gleichzeitig an verschiedenen Zonen über mindestens eine Stunde. Abnahme Tizen Version 1 (Abschnitt 13).

### Teil C – Fire TV mit Vega OS

**V0 – Vorbereitung**
Vega-Stick beschaffen (mache ich). Vega SDK im Home-Verzeichnis, Vega Virtual Device starten, Gerät im Entwicklerkonto für den Entwicklermodus registrieren (Login mache ich). `VEGA_DEVICE` in `config/local.env.example` ergänzen. Klären und dokumentieren: Engine-Version der Vega WebView, Laden gebündelter Dateien, WebSocket/HTTP zum LAN-Core aus der WebView, UDP/Multicast für Turbo Modules, Persistenz der Speicher. Passt die Engine-Version nicht zum Ausgabeziel aus 6.1, mich fragen.

**V1 – Debug-Kette**
Minimale Vega-App mit WebView und gebündelter Testseite bauen, auf Virtual Device und Gerät installieren, starten. **Nachweis:** Konsolenausgabe und ein absichtlich ausgelöster JS-Fehler aus der WebView ausgelesen, Nachricht hin und zurück über die Brücke, Screenshot, automatisierter Tastendruck.

**V2 – Web-Kern auf Vega**
`vega/` als Hülle um `web/`, Implementierung von `Platform` über die Brücke. Verbindung zum Core, Freigabe, Token-Persistenz über App- und Geräteneustart, Zonen, Now Playing. Discovery-Spike V-D1; sonst D2–D4. Ist die direkte Verbindung nicht stabil herstellbar: dokumentieren und mich fragen.

**V3 – Steuerung, Stabilität, Abnahme Vega**
Media-Tasten, Bildschirm an, Reconnect, Langzeittest wie T5, Speicher- und Ruckelmessung. Fire TV (Fire OS), Frame und Vega-Stick gleichzeitig an verschiedenen Zonen über mindestens eine Stunde. Abnahme Vega Version 1 (Abschnitt 13). Ergebnis der Messungen als Grundlage für Entscheidung 5.

### Danach

**F4 – Feinschliff aller Plattformen** nach den Erfahrungen aus Tizen und Vega (Abweichungen zwischen den Plattformen beseitigen).

**F5 – Echo Show 15:** Touch, LAT-Checkliste und Anleitung (5.2). Die Einreichung nehme ich vor.

**W – Fire OS auf den Web-Kern (nur nach Entscheidung 5):** Android-Hülle mit WebView um `web/`, Discovery weiter nativ über den vorhandenen Kotlin-Code, Übergabe von Host/Port über die Brücke. Erst nach Messung auf dem 1-GB-Stick (Ruckeln, Speicher, Startzeit) gegen die Kotlin-App; die Kotlin-App bleibt bis zur Abnahme der Hülle die ausgelieferte Version.

**S – Appstore (nur nach Entscheidung 4):** Demo-Modus, Metadaten, LAT, Einreichung (5.3).

## 11. Teststrategie – Claude Code testet selbst

### 11.1 Grundregel
Eine Funktion gilt erst als fertig, wenn Claude Code sie selbst automatisiert geprüft hat und den Nachweis vorweisen kann: grüner Testlauf, Screenshot oder Log-Auszug. „Kompiliert“ ist kein Nachweis. **Nie behaupten, dass etwas auf einem Gerät funktioniert, solange es dort nicht tatsächlich getestet wurde.** Ein Test auf dem Vega Virtual Device ersetzt den Gerätetest nicht. In der README-Statustabelle steht jede Funktion pro Plattform in genau einer der Spalten „automatisiert“, „auf dem Gerät“, „offen“.

### 11.2 Umgebung
1. VM 192.168.1.198/24 im Segment von Core und Geräten; SOOD-Multicast von der VM verifiziert.
2. Android: `scripts/dock adb connect $FIRE_TV_IP:5555` (eingerichtet).
3. Tizen (ab T1): `sdb` zum Frame (Port 26101 laut `remote-device`-Skill).
4. Vega (ab V0): Vega CLI zum Gerät im Entwicklermodus; Vega Virtual Device auf der VM, sofern die VM die Voraussetzungen erfüllt **[zu prüfen]**.
5. Geräte-IPs, Testzone und Signier-Pfade in `config/local.env` (gitignored).

### 11.3 Ebenen
1. **Unit-Tests ohne Gerät:** Android `scripts/dock ./gradlew test` (vorhanden für SOOD, MOO, Reducer, Resolver, Backoff, Fortschritt, Lyrics); Web-Kern Vitest. Ab F3 beide gegen `shared/testvectors/`. Dazu Tests des Brückenformats der Vega-Hülle.
2. **Fake Roon Core:** vorhanden in Kotlin (Fixtures, SOOD-Responder, Restart-, Watchdog- und Rediscovery-Szenarien). Für den Web-Kern entweder den Kotlin-Fake-Core als eigenständigen Testserver startbar machen oder einen kleinen Node-Testserver auf denselben Fixtures bauen; der einfachere Weg wird gewählt und begründet.
3. **UI-Screenshots ohne Gerät:** Android Roborazzi (vorhanden; Echo-Show-Format ergänzen in F5); Web-Kern Playwright im Desktop-Chromium bei 1920×1080 und 3840×2160. Desktop-Chromium ist neuer als die Geräte-Engines; ersetzt den Gerätetest nicht.
4. **Echtes Gerät + echter Core:** 11.4.

### 11.4 Gerätetests
**Fire TV (Fire OS):** `scripts/deploy-test.sh`, Maestro-Flows (`maestro/`), `screencap`, `logcat` (Tag `Roomscreen`), Debug-Broadcasts (`DUMP_STATE`, `SET_HOST`, `SELECT_ZONE`, `SET_LYRICS` …), Speicher- und Ruckelmessung über die vorhandenen Skripte. Hinweis aus `ARCHITECTURE.md` 9.6: Maestro liest die Oberfläche auf dem Stick langsam, solange animiert wird; Flows bei pausierter Zone navigieren und Wirkung per `DUMP_STATE` prüfen.

**Tizen:** bauen, signieren, als Update installieren (ob eine Neuinstallation mit gleichem Zertifikat die App-Daten behält **[zu prüfen]**), Start im Debug-Modus, CDP, Playwright-Flow, Geräte- oder CDP-Screenshot, Logs mit Präfix `Roomscreen` über CDP bzw. `dlog-analyzer`, Tasten per CDP (ob das registrierte Media-Tasten auslöst **[zu prüfen]**), Debug-Hooks im Debug-Build analog zu Android, Skript `tizen/scripts/deploy-test.sh`.

**Vega:** bauen, installieren über Vega CLI, Start, WebView-Inspektion per Chrome DevTools, Playwright per CDP falls möglich, Logs mit Präfix `Roomscreen`, Tasten über `inputd-cli`, Debug-Hooks wie oben, Skript `vega/scripts/deploy-test.sh`. Ob ein Update-Install die App-Daten behält **[zu prüfen]**.

**Gegenseite:** `roon-cli` startet, pausiert und wechselt Titel auf der Testzone.

### 11.5 Regeln
- Signierschlüssel bzw. Zertifikate bleiben dauerhaft dieselben (Ausnahme: der einmalige Wechsel auf den Release-Schlüssel in F3, nach Rückfrage). Kein Schlüssel, kein Zertifikat, kein Token ins Repo.
- Keine Deinstallation und kein Zurücksetzen der App-Daten ohne Rückfrage.
- Nur die Testzone verwenden (derzeit „Büro“, laut Skripten; `ROON_TEST_ZONE`).
- Pro Phase mindestens ein automatisierter Gerätetest für die Abnahmekriterien der Phase.
- Nach jedem sinnvollen Schritt committen und pushen.

### 11.6 Nicht automatisierbar
- Erstfreigabe jeder neuen `extension_id` in Roon (mache ich).
- Neustart des Roon Cores für den Reconnect-Test (mache ich nach Ansage).
- Drücken der physischen Fernbedienungen (mache ich nach Ansage).
- Samsung-Account-Login und Registrierung des Vega-Geräts im Amazon-Entwicklerkonto, falls interaktiv.
- Beschaffung des Vega-Sticks.
- Musik auf der Testzone erstmals starten, wenn sie keine Warteschlange hat.
- Echo Show 15 (nur LAT).
- Anfrage bei Roon Labs zur Markennutzung (nur bei Entscheidung 4).

## 12. Erwartete Ergebnisse

1. `docs/ARCHITECTURE.md` (vorhanden; ergänzen um Release-Signierung, Web-Kern und Plattform-Schnittstelle, Discovery auf Tizen und Vega, Speicherorte, Fake Core für den Web-Kern).
2. `docs/SPEC.md`.
3. Fire OS: signierte Release-APK.
4. Tizen: Quellcode der Hülle, Build-Konfiguration, signiertes `.wgt`, Signier- und Installationsanleitung.
5. Vega: Quellcode der Hülle, `.vpkg`, Installationsanleitung für den Entwicklermodus.
6. `web/` mit Kern, Oberfläche und Plattform-Schnittstelle; `shared/` mit Fixtures, Testvektoren, Design-Tokens, Texten; Testskripte aller Plattformen.
7. README: Installation Fire TV (vorhanden), Samsung und Vega, Freigabe in Roon, Bedienung, Einstellungen, bekannte Einschränkungen (inkl. LRCLIB-503, Vega nur per Entwicklermodus oder Appstore), Statustabelle aller Plattformen.
8. GitHub-fertig: Lizenz, `.gitignore`, keine Secrets, Markenhinweis für alle drei Marken, Liste der Drittlizenzen (inkl. Comfortaa, node-roon-api im Web-Kern, Vega-Bibliotheken).

## 13. Abnahmekriterien

### Fire TV Version 1, auf dem Fire TV Stick HD (AFTSS, mit Liedtexten)
1. Release-APK per ADB installieren.
2. App starten; der Core wird gefunden oder nach manueller Eingabe verbunden.
3. Extension einmalig in Roon aktivieren.
4. Zone wählen.
5. Musik auf dieser Zone über Roon starten.
6. Cover, Titel und Künstler erscheinen automatisch.
7. Der Fortschrittsbalken läuft passend zur Musik.
8. Bei Titelwechsel aktualisiert sich die Anzeige selbstständig.
9. Play/Pause und Titelwechsel funktionieren über die **physische** Fire-TV-Fernbedienung.
10. Synchronisierte Liedtexte erscheinen passend, wenn LRCLIB sie liefert und erreichbar ist.
11. Nach Beenden und Neustart verbindet sich die App ohne erneute Einrichtung mit derselben Zone; Einstellungen bleiben erhalten.
12. Nach Neustart des Roon Cores verbindet sich die App ohne Eingriff wieder.

### Tizen Version 1, auf dem Samsung The Frame
1. App aus dem App-Menü starten.
2. Verbindung zum Core – automatisch oder nach Eingabe von Host (und ggf. Port).
3. Extension einmalig in Roon aktivieren.
4. Zone wählen.
5. Musik über Roon starten.
6. Korrektes Cover, Titel und Künstler.
7. Fortschrittsbalken läuft passend zur Musik.
8. Bei Titelwechsel aktualisiert sich die Anzeige selbstständig.
9. Play/Pause und Titelwechsel über die Samsung-Fernbedienung.
10. Synchronisierte Liedtexte erscheinen passend, wenn LRCLIB sie liefert und erreichbar ist.
11. Nach Beenden und Neustart der App sowie nach Aus- und Einschalten des Fernsehers bleiben Einstellungen und Freigabe erhalten.
12. Nach Neustart des Roon Cores verbindet sich die App ohne Eingriff wieder.
13. Fire TV und Frame zeigen gleichzeitig verschiedene Zonen, ohne sich gegenseitig zu trennen.
14. Kein zusätzlicher Server nötig.

### Vega Version 1, auf einem Fire TV Stick mit Vega OS
1. App im Entwicklermodus (oder über LAT) installieren und aus dem App-Menü starten.
2. Verbindung zum Core – automatisch oder nach Eingabe von Host (und ggf. Port).
3. Extension einmalig in Roon aktivieren.
4. Zone wählen.
5. Musik über Roon starten.
6. Korrektes Cover, Titel und Künstler.
7. Fortschrittsbalken läuft passend zur Musik.
8. Bei Titelwechsel aktualisiert sich die Anzeige selbstständig.
9. Play/Pause und Titelwechsel über die physische Fire-TV-Fernbedienung (oder dokumentierte Abweichung, falls Vega das nicht zulässt).
10. Synchronisierte Liedtexte erscheinen passend, wenn LRCLIB sie liefert und erreichbar ist.
11. Nach Beenden und Neustart der App sowie nach Aus- und Einschalten bleiben Einstellungen und Freigabe erhalten.
12. Nach Neustart des Roon Cores verbindet sich die App ohne Eingriff wieder.
13. Fire TV (Fire OS), Frame und Vega-Stick zeigen gleichzeitig verschiedene Zonen, ohne sich gegenseitig zu trennen.
14. Kein zusätzlicher Server nötig; die App lädt keine Inhalte außer vom Core und von LRCLIB.

## 14. Entscheidungen und offene Punkte

### Entscheidungen, die ich treffe

1. **Speicher im Liedtext-Modus** (vor F3). Die Vorgabe ±10 MB gegenüber Phase 2 wird verfehlt (10–50 MB mehr, überwiegend löschbarer GPU-Cache, kein Wachstumstrend). Optionen: (a) Toleranz für den Liedtext-Modus auf einen festen Höchstwert anheben (z. B. „TOTAL PSS bleibt nach 30 Titelwechseln unter 200 MB, kein Trend“), (b) Ursache isolieren und den GPU-Cache begrenzen (z. B. Text-Rendering prüfen, `onTrimMemory` nutzen), dann erneut messen. Claude Code legt mir vorher die Messwerte und den Aufwand von (b) vor.
2. **„via roon“-Hinweis** (vor F3). Laut README steht die Freigabe des Aussehens durch mich noch aus. Das Wort „roon“ wird in einer eigenen runden Schrift (Comfortaa) gesetzt. Die Markenregel dieses Plans erlaubt den Namen nur beschreibend. Optionen: (a) in der normalen UI-Schrift, gleich groß wie „via“, standardmäßig aus; (b) so lassen. Bis zur Entscheidung keine weitere Arbeit daran und nicht in den Web-Kern übernehmen.
3. **Release-Schlüssel** (in F3). Einmalige Deinstallation auf dem Fire TV (Token und Freigabe gehen verloren, neues „Enable“). Vorschlag: in F3, vor der Abnahme, damit die abgenommene APK die ist, die später auch über LAT verteilt wird.
4. **Amazon Appstore** (frühestens nach F3). Ob Roomscreen öffentlich im Appstore erscheinen soll. Voraussetzung: schriftliche Erlaubnis von Roon Labs zur Nennung von „Roon“; dann Demo-Modus und LAT (5.3, Phase S). Für Vega-Geräte ist der Appstore der einzige Weg zu anderen Nutzern.
5. **Fire OS auf den Web-Kern** (nach V3). Ob die Kotlin-App durch eine WebView-Hülle um den Web-Kern ersetzt wird (Phase W). Grundlage: Ruckeln, Speicher und Startzeit des Web-Kerns auf dem 1-GB-Stick im Vergleich zur Kotlin-App und der Aufwand, zwei Oberflächen synchron zu halten.
6. **Speicher, Entscheidung 14.1 a (2026-10-19, festgehalten).** Der feste Höchstwert gilt: TOTAL PSS nach 30 Titelwechseln mit Liedtext und scharfem Künstlerbild unter 200 MB, kein Trend. Messwert 196,3 MB am Messpunkt (erfüllt, Reserve 3,7 MB), Spitze 218 MB zwischen den Wechseln 1 und 21 (offen benannt, transienter GPU-Cache; `docs/ARCHITECTURE.md` 11.5). Keine Code-Änderung; `onTrimMemory` mit Leeren des Coil-Speichercaches nur auf Zuruf des Nutzers.

### Offene technische Punkte [zu prüfen]

Fire TV (Fire OS):
1. Tastencodes der physischen Fernbedienung.
2. Reconnect nach Neustart des echten Cores.
3. Ruckeln Liedtext-Ansicht und scharfer Hintergrund.
4. LAT-Bedingungen für den Echo Show 15.

Tizen:
5. Tizen-Version und Modell des Frame.
6. Bündeln von node-roon-api; Token-Speicherung und Persistenz; Eignung für Watchdog/Rediscovery.
7. WebSocket aus der Tizen-App am Core; Bilder per `<img>`.
8. Tizen Sockets Extension: Privilegien, Zertifikatsstufe, Multicast-Empfang.
9. Antwortet der Core auf Unicast-SOOD?
10. Wo Roon dem Benutzer den Port anzeigt.
11. Gerätename auf Tizen für den Anzeigenamen der Extension.
12. `config.xml`-`access` für LAN-WebSocket und HTTP; CORS bei LRCLIB mit `Lrclib-Client`.
13. Media-Tasten-Registrierung, Zurück-Taste, Bildschirmschoner.
14. Screenshot vom Seriengerät; automatisierte Tastendrücke.
15. App-Daten bei Neuinstallation; Haltbarkeit von Entwickler-Apps und Zertifikat.
16. Komponenten von Apps2Samsung.

Vega:
17. Engine-Version der Vega WebView; Laden gebündelter Dateien statt URL.
18. WebSocket und HTTP-Bilder aus der WebView zum LAN-Core; CORS bei LRCLIB.
19. UDP und Multicast-Empfang für C++-Turbo-Modules; Verhalten des WLAN-Stromsparmodus beim Stick HD 2026.
20. Persistenz der Speicher über App- und Geräteneustart; App-Daten bei Update-Install.
21. Media-Tasten ohne eigene Medienwiedergabe; Bildschirm an.
22. Playwright per CDP, Screenshot-Weg, `inputd-cli` für Tastendrücke.
23. Vega SDK auf der VM ohne sudo; Vega Virtual Device auf der VM.
24. LAT für Vega-Pakete; Format der Vega-App-ID; Lizenzen der Vega-Bibliotheken.

Appstore (nur bei Entscheidung 4):
25. Feld für Prüfhinweise bzw. Demo-Zugang bei der Einreichung; Kosten des Entwicklerkontos; Bewertung der Media-Tasten-Nutzung und von „Bildschirm an“ im Prüfprozess.

## 15. Erledigt (Nachweis in `docs/ARCHITECTURE.md` und `docs/STATUS.md`)

Menüpfad der Freigabe („Settings → Extensions“, Roon für Mac 2.73); `register` vor/nach Freigabe; `CONTINUE Registered`; Token-Wiederverwendung; Port aus SOOD; `three_line`-Belegung; Bilder per HTTP ohne Token; Künstlerbilder über `artist_image_keys`; Ping-Service; Seek-Rate 1,00 Hz; Ablauf beim Titelwechsel; Gruppenverhalten; Verdrängung bei gleicher `extension_id` und Kennung pro Installation; Fire OS 7.7.1.7 / SDK 28; Multicast mit `MulticastLock` auf Fire OS; Tastencodes eingespeister Media-Tasten; Hintergrund ohne Live-Blur; LRCLIB-Verhalten (404, 503, Varianten, Suche); Liedtext-Cache; Bildschirm-an per Wakelock.

## 16. Quellen (Stand 18.10.2026)

- Repository, Statustabelle und Befunde: https://github.com/knoellp/Roomscreen (`README.md`, `docs/STATUS.md`, `docs/DEVELOPMENT.md`, `docs/ARCHITECTURE.md`)
- Roon API für JavaScript, README: https://github.com/RoonLabs/node-roon-api
- Roon-Community, Port nicht fest / Discovery verwenden: https://community.roonlabs.com/t/roon-api-on-build-880-connection-refused-error/181619 und https://community.roonlabs.com/t/port-change-for-roon-display/191382
- Samsung Web Engine Specifications: https://developer.samsung.com/smarttv/develop/specifications/web-engine-specifications.html
- Samsung WebAssembly: https://developer.samsung.com/smarttv/develop/extension-libraries/webassembly/webassembly.html
- Samsung Tizen Sockets Extension: https://developer.samsung.com/smarttv/develop/extension-libraries/webassembly/tizen-sockets-extension/tizen-sockets-extension.html
- Samsung FAQ Networking: https://developer.samsung.com/smarttv/develop/faq/networking-and-connectivity.html
- Samsung tizen-agent-skills: https://github.com/Samsung/tizen-agent-skills und `tizen-sdk-skills/docs/SKILLS_REFERENCE.en.md`
- Apps2Samsung: https://github.com/Apps2Samsung/Apps2Samsung
- Sonance 3: https://github.com/MrSimmo/sonance3
- LRCLIB API: https://lrclib.net/docs
- Amazon: Fire OS und Vega OS (SDK-Übersicht): https://developer.amazon.com/sdk.html
- Amazon: Test Criteria for Amazon Appstore Apps (Stand 18.09.2026): https://developer.amazon.com/docs/app-testing/test-criteria
- Amazon: Intellectual Property Policy: https://developer.amazon.com/docs/policy-center/intellectual-property.html
- Amazon: Submit Your App to the Amazon Appstore: https://developer.amazon.com/docs/app-submission/submitting-apps-to-amazon-appstore.html
- Amazon: Vega App-Einreichung: https://developer.amazon.com/docs/vega/0.24/app-submission
- Amazon: Develop your app with Vega WebView: https://developer.amazon.com/docs/vega/latest/develop-your-app-with-webview.html
- Amazon: Vega Build-Übersicht (Turbo Modules, WebView): https://developer.amazon.com/docs/vega/latest/build-apps-overview.html
- Amazon: Run your app on Vega Virtual Device or Fire TV (Entwicklermodus): https://developer.amazon.com/docs/vega/0.23/run-apps.html
- Amazon: Port a Fire OS app to Vega (App Porting): https://developer.amazon.com/docs/adbt/port-fire-os-app-to-vega
- Amazon-Entwicklerforum, Entwicklerbericht WLAN-Stromsparen Fire TV Stick HD (Vega OS 1.2): https://community.amazondeveloper.com/t/fire-tv-stick-hd-vega-os-1-2-wi-fi-shows-connected-but-the-device-stops-answering-arp-ping-tcp-udp-from-the-lan-until-the-network-is-forgotten-and-re-joined/29220
- AFTVnews zur Aussage „alle künftigen Fire TV Sticks laufen unter Vega“: https://www.aftvnews.com/?p=46379
