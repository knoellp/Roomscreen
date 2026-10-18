# Auftrag: Phase F3b – Echo-Show-Vorbereitung auf dem AFTSS und APK-Releases

> Auftrag an Claude Code für `knoellp/Roomscreen`. Diese Phase wird in `docs/PLAN.md` (v5) zwischen F3 und T0 eingefügt; F5 (Echo Show 15) wird auf die hier beschriebenen Vorarbeiten verwiesen. Die Kennzeichnung [verifiziert] / [geprüft] / [zu prüfen] gilt wie in `docs/PLAN.md`. Stand 18.10.2026.
>
> Ziel: Nach F3b ist die Fire-OS-App so gebaut und auf dem Fire TV Stick HD (AFTSS) getestet, dass sie auf dem Echo Show 15 ohne weitere Entwicklung über Live App Testing abgenommen werden kann. Außerdem gibt es signierte, versionierte APKs, die Nutzer ohne ADB installieren können.

## 1. Warum auf dem AFTSS entwickelt wird

Der Echo Show 15 (beide Generationen, 2021 und 2024) läuft laut Amazons Gerätetabelle mit Fire OS 7 auf Android 9 (API 28), genau wie der AFTSS [geprüft, Amazon Device Specifications]. Unterschiede:

| Punkt | AFTSS | Echo Show 15 | Quelle |
|---|---|---|---|
| ADB | ja | **nein** | Amazon-Blog 12/2022 [geprüft] |
| Sideloading | ja | **nein**, auch nicht über Umwege; Amazon hat die Lücke über die System-Dateien-App geschlossen | AFTVnews, Stand 12/2024 [geprüft] |
| Installation von Entwickler-Builds | ADB | **nur Live App Testing (LAT)** | Amazon-Blog [geprüft] |
| Eingabe | Fernbedienung | **Touch primär**, Fire-TV-Fernbedienung optional (Gen 2: beiliegend); zeigt die App keine Touch-Unterstützung, blendet Fire TV eine virtuelle Fernbedienung ein | Amazon-Blog, AFTVnews [geprüft] |
| Bildschirm | TV, 3 m Abstand | 15,6" 1080p, Küchenabstand 1–2 m | Amazon [geprüft] |
| Logs | logcat | **keine** | folgt aus „kein ADB“ |
| RAM | 1 GB | 3 GB (Gen 1) | Amazon [geprüft] |
| Fire-TV-Oberfläche | vollständig | reduziert; Einstellungsmenü fehlt | Amazon-Blog, AFTVnews [geprüft] |
| Rückfall auf den Alexa-Startbildschirm nach Inaktivität | nein | Nutzerberichte sprechen von einem Timeout nach einigen Minuten im Browser; ob das auch Fire-TV-Apps mit Wakelock betrifft, ist **[zu prüfen in F5]** | Home-Assistant- und SharpTools-Foren, keine Amazon-Quelle |

Konsequenz: Jede Funktion wird auf dem AFTSS gebaut und dort auch für Touch getestet. Auf dem Echo Show wird nur noch abgenommen. Ein Fehler, der erst dort auffällt, kostet einen vollen LAT-Zyklus.

## 2. Zu bauen

### 2.1 Touch-Bedienung (vollständig, nicht nur ergänzend)

Alles, was heute per Steuerkreuz erreichbar ist, muss per Fingertipp erreichbar sein, ohne dass die Fernbedienung gebraucht wird:

- **Now Playing:** Tipp auf den Bildschirm zeigt die Steuerleiste (wie heute jede Taste). Sie bleibt 5 s sichtbar oder laut Einstellung dauerhaft.
- **Steuerleiste:** jede Schaltfläche tippbar; Trefferfläche mindestens 48 dp (Android-Richtlinie) bzw. Amazon-Vorgabe 7 × 7 mm für Touch [geprüft, Test Criteria 3.10]. Fokusrahmen bleibt für die Fernbedienung erhalten.
- **Zonenliste, Einstellungen, Liedtext-Ansicht:** Einträge tippbar, Listen per Finger scrollbar, Umschalter per Tipp.
- **Manuelle Host/Port-Eingabe:** Tipp in das Feld öffnet die Bildschirmtastatur; Amazon blendet auf dem Echo Show automatisch eine Touch-Tastatur ein, wenn die App keine eigene hat [geprüft, Amazon-Blog]. Auf dem AFTSS wird dieses Verhalten mit der Standard-Tastatur geprüft.
- **Zurück per Touch:** Es gibt keine Zurück-Geste. Zonenliste und Einstellungen bekommen eine sichtbare Schließen-Schaltfläche; die Liedtext-Ansicht lässt sich über die Steuerleiste verlassen.
- **Diagnose-Overlay:** zusätzlich zur Tastenfolge über eine Touch-Geste erreichbar (Vorschlag: fünfmal tippen auf die Uhrzeit-Anzeige innerhalb von 3 s). Darf nicht versehentlich auslösen.
- Keine Touch-spezifischen Elemente, wenn nur eine Fernbedienung angeschlossen ist (Test Criteria 2.12 verlangt, dass Touch-Elemente bei Controller-Eingabe zurücktreten) [geprüft]. Lösung: Die Steuerleiste sieht in beiden Fällen gleich aus; es gibt keine separaten Touch-Bedienelemente.
- Amazons Dokument „Touch UI best practices“ für Fire TV vor der Umsetzung lesen (Link im Amazon-Blog 12/2022) und Abweichungen in `docs/ARCHITECTURE.md` begründen.

### 2.2 Layout, Dichte, Ausrichtung

- Die App ist heute auf 1920×1080 auf einem TV ausgelegt. Der Echo Show hat dieselbe Auflösung, aber eine andere physische Größe; welche Dichte-Klasse Fire OS dort meldet, ist **[zu prüfen]** (Live App Testing in F5 oder Entwicklerforum). Vorsorglich: alle Maße in dp, Layout mit `tvdpi`, `hdpi` und `xhdpi` in Roborazzi rendern, nichts darf abgeschnitten werden.
- Lesbarkeit aus 1–2 m: Schriftgrößen bleiben; der Titel darf größer sein als auf dem TV nötig, nicht kleiner.
- **Hochformat:** Der Echo Show 15 Gen 1 kann hochkant montiert werden [geprüft, Amazon Device Specifications]. Ob der Fire-TV-Modus dann hochkant läuft, ist **[zu prüfen in F5]**. Vorgabe bis dahin: Die App bleibt auf Querformat festgelegt (Test Criteria 2.8 verlangt für Fire TV Querformat [geprüft]) und darf bei einem Konfigurationswechsel keinen Zustand verlieren. Kein eigenes Hochformat-Layout bauen.
- Overscan: Auf dem Echo Show gibt es keinen; die Safe-Area-Ränder dürfen dort nicht als sichtbarer leerer Rahmen wirken. Prüfen, ob die Ränder im Vergleich zum TV-Bild störend sind (Screenshot-Vergleich); gegebenenfalls Rand vom Gerätetyp abhängig machen, **ohne** auf dem AFTSS etwas zu ändern.

### 2.3 Lebenszyklus und Bildschirm

- `FLAG_KEEP_SCREEN_ON` wie bisher. Zusätzlich im Diagnose-Overlay anzeigen, seit wann die App im Vordergrund ist, damit sich ein Rückfall auf den Alexa-Startbildschirm (Abschnitt 1) in F5 nachweisen lässt.
- Verhalten bei `onPause`/`onResume` durch Alexa-Overlays (Sprachbefehl, Timer, Anruf) auf dem AFTSS mit dem Sprachsuche-Overlay simulieren: Zustand bleibt erhalten, keine Doppelverbindung, Reconnect nach Rückkehr (Test Criteria 4.2 verlangt `onPause`/`onResume` bei Sprachsuche [geprüft]).
- Audio-Fokus: Die App spielt keinen Ton und darf keinen Audio-Fokus anfordern (sonst unterbricht sie Alexa-Musik auf dem Echo Show). Im Code prüfen, dass keine Bibliothek es implizit tut.

### 2.4 Diagnose-Overlay (ersetzt logcat)

Pflicht, weil es auf dem Echo Show keine andere Sicht in die App gibt:

- Build: Version, versionCode, Git-Commit, Signatur-Fingerabdruck (SHA-256, gekürzt), Gerätemodell, Fire-OS-Version, gemeldete Dichte und Bildschirmgröße in dp.
- Verbindung: Zustand, Core-Adresse, Weg (gespeichert/Discovery/manuell), Discovery-Ergebnis, Token vorhanden ja/nein, letzter Fehler, Uptime im Vordergrund.
- Eingabe: die letzten 20 Eingabeereignisse (Tastencode oder Touch mit Koordinaten) mit Zeitstempel. Damit lässt sich in F5 prüfen, welche Tasten die Fernbedienung des Echo Show liefert.
- Log: Ringpuffer der letzten 200 Zeilen mit Präfix `Roomscreen`, scrollbar per Finger.
- Export: Schaltfläche „Diagnose kopieren“ ist auf dem Echo Show ohne ADB wertlos. Stattdessen **Option für meine Entscheidung** (Abschnitt 8): Senden des Puffers per HTTP POST an eine vom Nutzer eingegebene LAN-Adresse, nur auf Knopfdruck, nur im Debug- und LAT-Build. Bis zur Entscheidung nicht bauen.

### 2.5 Manifest und Kompatibilität

- `uses-feature android.hardware.touchscreen required="false"` und `android.software.leanback required="false"` prüfen (vermutlich schon so; sonst Echo Show oder Fire TV fallen aus der Kompatibilität).
- `LEANBACK_LAUNCHER`-Intent-Filter und die Hauptkategorie bleiben; ohne Hauptkategorie startet die App nicht aus dem Fire-TV-Launcher und fällt bei Amazon durch [geprüft, Amazon Submission-Doku].
- Keine der von Amazon unerwünschten Berechtigungen (Kamera, Telefon, SMS, Standort, Mikrofon) [geprüft, Test Criteria Gruppe 1]. Manifest-Test, der das erzwingt.
- Netzwerk: Cleartext-HTTP zum Core bleibt nötig; `networkSecurityConfig` so eng wie möglich (nur private Adressbereiche), nicht global.

### 2.6 Ladezustand

Aus F3 übernommen: Der Verbindungsbildschirm zeigt vom ersten Frame an einen sichtbaren Ladezustand, der ausführliche Hinweis kommt weiterhin nach 20 s (Test Criteria 2.3: Hinweis spätestens nach 15 s [geprüft]).

## 3. Zu testen auf dem AFTSS

Grundregel aus `docs/PLAN.md` 11.1: Nachweis durch grünen Test, Screenshot oder Log. Der AFTSS hat keinen Touchscreen, nimmt aber Touch-Ereignisse per ADB an.

### 3.1 Touch ohne Touchscreen

- `adb shell input tap X Y` und `input swipe` treffen die App wie echte Berührungen. Damit Maestro-Flows mit `tapOn` und `swipe` schreiben (Maestro übersetzt sie in dieselben Ereignisse).
- Jeder Flow aus `maestro/`, der heute mit Tasten navigiert, bekommt eine Touch-Variante. Wirkung wie bisher per `DUMP_STATE` prüfen, nicht nur optisch (Hinweis aus `ARCHITECTURE.md` 9.6: Maestro liest die Oberfläche bei Animationen langsam).
- Flows: Steuerleiste per Tipp öffnen, Play/Pause/Weiter/Zurück tippen, Zone per Tipp wechseln, Einstellungen per Tipp öffnen und einen Wert ändern, Liedtext-Ansicht öffnen und schließen, Host/Port per Bildschirmtastatur eingeben, Diagnose-Overlay per Geste öffnen, Einstellungen per Schließen-Schaltfläche verlassen.
- Mischbetrieb: Flow, der Tasten und Tipps abwechselt; danach darf kein Fokus „verloren“ sein (Steuerkreuz muss weiter funktionieren).

### 3.2 Ohne Gerät

- Roborazzi: alle Bildschirme zusätzlich in `tvdpi`, `hdpi`, `xhdpi` bei 1920×1080; Referenzbilder einchecken.
- Compose-UI-Tests mit `performClick`/`performTouchInput` für Trefferflächen (≥ 48 dp) und für die Fünffach-Tipp-Geste.
- Optional Android-Emulator im Docker-Image (API 28, Tablet-Profil 1920×1080): nur, wenn er im Image ohne sudo läuft; sonst weglassen und in `ARCHITECTURE.md` vermerken.

### 3.3 Lebenszyklus

- Sprachsuche-Overlay auf dem AFTSS auslösen (`adb shell input keyevent` mit dem Sprachsuche-Code, falls verfügbar; sonst Home und Rückkehr), währenddessen Titelwechsel auf der Testzone, danach Zustand und einzige Verbindung per `DUMP_STATE` und `roon-cli` prüfen.
- Kein Audio-Fokus: `dumpsys audio` zeigt keinen Fokus-Eintrag der App.

### 3.4 Abnahme F3b auf dem AFTSS

1. Alle Touch-Flows grün, Screenshots in `build/device-artifacts/`.
2. Alle Fernbedienungs-Flows aus F3 weiterhin grün (keine Regression).
3. Diagnose-Overlay zeigt alle Felder aus 2.4 mit echten Werten (Screenshot).
4. Manifest-Test grün.
5. Roborazzi-Referenzen für drei Dichten eingecheckt.
6. README-Statustabelle: neue Spalte „Touch (AFTSS, automatisiert)“; Echo Show bleibt „offen“ bis F5.

## 4. APK-Releases

### 4.1 Grundsätze

- Jedes Release ist mit dem **Release-Schlüssel aus F3** signiert. Nur so können Nutzer Updates über eine bestehende Installation legen; ein Schlüsselwechsel erzwingt Deinstallation und neues „Enable“ in Roon.
- `versionCode` steigt bei jedem Release streng monoton; `versionName` folgt `MAJOR.MINOR.PATCH`. Git-Tag `vMAJOR.MINOR.PATCH` auf dem Release-Commit.
- Nur Release-Builds werden veröffentlicht, nie Debug-Builds.
- Jedes Release bekommt `SHA256SUMS` und einen Eintrag in `CHANGELOG.md`.

### 4.2 Bau auf GitHub Actions

Das ist möglich und für dieses Repo kostenlos: Standard-Runner sind in öffentlichen Repositories gratis [geprüft, GitHub-Doku zur Abrechnung]. Vorgaben:

- **Auslöser:** `release: types: [published]`. Du legst im Web oder per `gh release create` ein Release auf einem Tag an; der Workflow baut und hängt die Dateien als Assets an. Zusätzlich `workflow_dispatch` für Probeläufe, die nur Workflow-Artefakte erzeugen und nichts veröffentlichen.
- **Build-Umgebung:** denselben Docker-Build wie `scripts/dock` verwenden (Image aus `docker/` im Workflow bauen oder aus einer Registry ziehen, Layer-Cache nutzen). Grund: Reproduzierbarkeit und dieselbe Toolchain wie lokal. Hinweis für die Alternative ohne Docker: GitHub entfernt seit Januar 2026 Android-SDK-Plattformen und Build-Tools unter Version 34 aus den Runner-Images; benötigte Versionen müssen dann per `sdkmanager` zur Laufzeit installiert werden [geprüft, actions/runner-images Issue 13469].
- **Signierung:** Keystore als Base64 in einem Repository-Secret, Passwörter und Alias als weitere Secrets; im Workflow in eine temporäre Datei schreiben, nach dem Build löschen. Signatur mit `apksigner verify --print-certs` prüfen und den Fingerabdruck in das Job-Log und in `SHA256SUMS` schreiben. **Entscheidung von mir nötig** (Abschnitt 8), ob der Release-Schlüssel bei GitHub liegen darf.
- **Schritte:** Checkout des Tags → `./gradlew test` → `assembleRelease` → Signatur prüfen → Dateinamen `Roomscreen-<versionName>-<versionCode>.apk` und eine zweite Kopie `Roomscreen.apk` (stabiler Name, siehe 4.4) → `SHA256SUMS` → Upload als Release-Assets mit `gh release upload` (GitHub CLI ist auf den Runnern vorhanden). Schlägt ein Schritt fehl, wird nichts hochgeladen.
- **Berechtigungen:** `permissions: contents: write` nur für den Release-Job; sonst `contents: read`.
- **Prüfung von Tag und Version:** Der Workflow bricht ab, wenn `versionName` im Build nicht zum Tag passt oder `versionCode` nicht größer ist als im vorigen Release (Vergleich über die Release-Liste per `gh`).

### 4.3 Ablage im Repository

Du möchtest die APKs zusätzlich im Repo haben. Das geht, mit zwei Einschränkungen, die ich benennen muss:

- Binärdateien in Git bleiben für immer in der Historie; bei einigen MB pro Release wächst das Repo mit jedem Release spürbar. Git LFS auf GitHub hat Kontingente (Speicher und Bandbreite), die bei vielen Downloads schnell erreicht sind; LFS ist deshalb nicht geeignet, wenn Nutzer die Dateien direkt laden sollen.
- Ein Workflow, der vom Release-Ereignis ausgelöst wird, läuft auf dem Tag, nicht auf `main`. Um die APK in `main` abzulegen, muss der Job `main` auschecken, die Datei hinzufügen und mit dem Workflow-Token committen und pushen. Das funktioniert, erzeugt aber einen zusätzlichen Commit pro Release, und Branch-Schutzregeln müssen das zulassen.

Vorgabe:

- **Primär:** GitHub Releases. Jedes Release trägt die versionierte APK, `Roomscreen.apk`, `SHA256SUMS` und die Release-Notizen. Alle Vorversionen bleiben dort dauerhaft erreichbar.
- **Zusätzlich im Repo:** Verzeichnis `releases/` mit der **aktuellen und den zwei vorherigen** versionierten APKs, `SHA256SUMS` und einer `releases/README.md` mit Installationsanleitung. Der Release-Job pflegt das Verzeichnis (hinzufügen, ältere entfernen) und committet nach `main`. Anzahl der Vorversionen ist meine Entscheidung (Abschnitt 8).
- `.gitattributes`: `*.apk binary`.

### 4.4 Installation für Nutzer ohne ADB

Fire TV mit Fire OS (nicht Echo Show, nicht Vega):

1. Einstellungen → Mein Fire TV → Entwickleroptionen → „Apps unbekannter Herkunft“ für die verwendete Lade-App erlauben. Den genauen Menüpfad auf dem AFTSS prüfen und mit Screenshot in der README belegen; Amazon hat die Entwickleroptionen in neueren Fire-OS-Versionen versteckt, der Weg dorthin gehört in die Anleitung **[zu prüfen auf dem AFTSS, der README-Text darf nur den dort belegten Pfad nennen]**.
2. Mit der App „Downloader“ (oder einem Browser) die stabile Adresse `https://github.com/knoellp/Roomscreen/releases/latest/download/Roomscreen.apk` öffnen. GitHub leitet `releases/latest/download/<asset-name>` auf das Asset des neuesten Releases weiter [geprüft, GitHub-Doku „Linking to releases“]. Deshalb der feste Dateiname `Roomscreen.apk` zusätzlich zur versionierten Datei.
3. Installieren; bei einem Update über eine bestehende Installation bleiben Token, Zone und Einstellungen erhalten (Test in 4.5).
4. Prüfsumme optional vergleichen.

README: Abschnitt „Install“ mit beiden Wegen (ADB für Entwickler, Downloader für Nutzer), Hinweis, dass die APK nur auf Fire OS läuft, und Hinweis auf den Echo Show (nur über LAT, Abschnitt 5). Der Signatur-Fingerabdruck steht in der README, damit Nutzer die Herkunft prüfen können.

### 4.5 Tests für die Release-Kette

- Probelauf des Workflows per `workflow_dispatch` auf einem Nicht-Release-Commit: Artefakt entsteht, nichts wird veröffentlicht.
- Erstes echtes Release `v1.0.0` nach Abnahme F3 (siehe Reihenfolge in Abschnitt 6).
- Auf dem AFTSS: Release-APK per Downloader von der stabilen URL laden und installieren (Maestro kann Downloader nicht bedienen; dieser Schritt wird einmal manuell gemacht und mit Screenshots belegt).
- Update-Test: Release N installiert, Zone und Einstellungen gesetzt, Release N+1 über die stabile URL installieren → Token, Zone und Einstellungen bleiben, kein neues „Enable“ (Test Criteria 3.8 [geprüft]).
- `apksigner verify` auf der heruntergeladenen Datei zeigt denselben Fingerabdruck wie die README.

## 5. Vorbereitung für Live App Testing (Übergabe an F5)

In F3b nur vorbereiten, nicht einreichen:

- Metadaten-Entwurf in `docs/store/`: Titel, Kurz- und Langbeschreibung (ohne Markenverletzung, „for Roon“ nur beschreibend), Kategorie, Sprachen en/de, Content-Rating-Antworten, Export-Compliance-Antwort, Datenschutzangaben (keine personenbezogenen Daten; an LRCLIB nur Titel-Metadaten).
- Bilder: Icon 512×512 PNG mit Transparenz [geprüft, Test Criteria 3.15], Icon 1280×720, 3–10 Screenshots vom AFTSS in 1920×1080 [geprüft].
- Checkliste der Test Criteria, die auch für LAT-Builds sinnvoll sind (Gruppe 2a, 3, 4): jede Zeile mit „erfüllt“ und Nachweis oder „offen“.
- Hinweis in die Checkliste: Das Testerkonto (mein Amazon-Konto, mit dem der Echo Show angemeldet ist) muss LAT-E-Mails empfangen können; Amazon versendet die Einladung per E-Mail und erwartet, dass sie am Computer geöffnet wird; Installation dann über „Get App“ auf der Testseite [geprüft, Amazon „Prepare App Testers“]. Ob die Einladung an ein Marketing-Opt-in gebunden ist, steht in einer Drittanleitung, nicht bei Amazon **[zu prüfen beim ersten Versuch]**.
- Ob der Echo Show 15 in der Geräteauswahl des Developer-Portals erscheint und ob LAT-Builds dort installierbar sind **[zu prüfen in F5]**.

## 6. Reihenfolge

1. F3 wie geplant abschließen (Release-Schlüssel, Abnahme Fire TV Version 1).
2. Release-Pipeline (4.2, 4.3) bauen und mit `workflow_dispatch` testen.
3. Erstes Release `v1.0.0` = die in F3 abgenommene APK. Installations- und Update-Test (4.5).
4. Touch, Overlay, Manifest, Dichten (Abschnitt 2 und 3).
5. Release `v1.1.0` mit Touch-Unterstützung. Update-Test von 1.0.0 auf 1.1.0.
6. LAT-Vorbereitung (Abschnitt 5) einchecken.
7. `docs/PLAN.md`, `docs/ARCHITECTURE.md`, README und `CLAUDE.md` nachziehen.
8. Danach T0.

## 7. Nicht automatisierbar (mache ich)

- Release im GitHub-Web oder per `gh release create` anlegen.
- Secrets für Keystore und Passwörter im Repository hinterlegen, falls Entscheidung 1 positiv ausfällt.
- Downloader-Installation auf dem AFTSS mit der Fernbedienung (nach Ansage, Claude Code prüft im Log).
- Später (F5): LAT-Einreichung, Echo-Show-Abnahme.

## 8. Entscheidungen, die ich treffe

1. **Release-Schlüssel bei GitHub?** (a) Keystore als Secret, Workflow signiert; bequem, aber der Schlüssel liegt bei GitHub, und jeder mit Schreibrecht auf Workflows könnte ihn über einen manipulierten Workflow auslesen. (b) Workflow baut unsigniert, ich signiere lokal im Docker-Image und lade die signierte APK hoch; sicherer, aber ein manueller Schritt pro Release. Bei einem Solo-Repo ohne weitere Mitwirkende ist (a) vertretbar; wenn später Fremde Pull Requests stellen, Secrets für Forks ohnehin nicht verfügbar. Vorschlag: (a).
2. **Anzahl der APKs im Repo:** Vorschlag aktuelle plus zwei Vorversionen; alle älteren nur in GitHub Releases.
3. **Diagnose-Export per HTTP POST** im Debug-/LAT-Build (2.4): ja oder nein.
4. **Geste für das Diagnose-Overlay:** Fünffach-Tipp auf die Uhrzeit, oder eine andere.

## 9. Offene technische Punkte [zu prüfen]

1. Dichte-Klasse und dp-Maße, die Fire OS auf dem Echo Show 15 meldet.
2. Hochformat im Fire-TV-Modus des Echo Show 15.
3. Rückfall auf den Alexa-Startbildschirm trotz Wakelock.
4. Echo Show 15 in der Geräteauswahl des Developer-Portals; LAT-Installation dort.
5. Menüpfad zu „Apps unbekannter Herkunft“ auf dem AFTSS mit aktueller Firmware.
6. Marketing-Opt-in als Voraussetzung für LAT-Einladungen.
7. Tastencodes der Fire-TV-Fernbedienung am Echo Show (über das Overlay in F5).

## 10. Quellen (Stand 18.10.2026)

- Amazon Device Specifications Echo Show: https://developer.amazon.com/docs/device-specs/device-specifications-echo-show.html
- Amazon Fire OS Overview (Gerätetabelle): https://developer.amazon.com/docs/fire-tv/fire-os-overview.html
- Amazon-Blog „Fire TV experience now available on Echo Show 15“ (12/2022, kein ADB, Touch, LAT): https://developer.amazon.com/apps-and-games/blogs/2022/12/fire-tv-on-echoshow
- Amazon Test Criteria (18.09.2026): https://developer.amazon.com/docs/app-testing/test-criteria
- Amazon „Prepare App Testers“ (LAT): https://developer.amazon.com/docs/app-testing/prepare-app-testers.html
- Amazon „Live Device Interaction – Physical Devices“ (nur für ausgewählte Partner): https://developer.amazon.com/docs/app-testing/live-device-interaction-physical.html
- AFTVnews, Sideloading auf dem Echo Show 15 gesperrt (Stand 12/2024): https://www.aftvnews.com/how-to-sideload-apk-apps-on-the-amazon-echo-show-15/
- GitHub-Doku, Abrechnung GitHub Actions (öffentliche Repos kostenlos): https://docs.github.com/en/billing/concepts/product-billing/github-actions
- GitHub-Doku, Linking to releases (`releases/latest/download/<asset>`): https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases
- actions/runner-images, Entfernung alter Android-SDK-Versionen (Issue 13469): https://github.com/actions/runner-images/issues/13469
