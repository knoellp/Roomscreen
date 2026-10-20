# Store-Metadaten, Entwurf (Deutsch)

Entwurf für Amazon Appstore / Live App Testing (LAT). Nicht eingereicht. Felder mit "[to be filled]" brauchen eigene Daten des Maintainers.

## Eintrag

| Feld | Wert |
|---|---|
| App-Titel | Roomscreen |
| Kategorie | Musik & Audio (bzw. die nächstliegende Fire-TV-Kategorie im Developer Portal) |
| Sprachen | Englisch (Standard), Deutsch |
| Support-E-Mail | <to be filled by the maintainer> |
| Support-Website | [to be filled] |
| URL der Datenschutzerklärung | [to be filled: `privacy.md` hosten] |
| Entwickler / Firma | [to be filled] |

## Kurzbeschreibung (max. 160 Zeichen)

```
Ein "Läuft gerade"-Bildschirm für Deine Roon-Zonen: Cover, Titel, Interpret, Fortschritt und synchronisierte Liedtexte auf dem großen Display.
```

(Zeichenzahl nach Änderungen erneut prüfen; Limit 160.)

## Langbeschreibung

```
Roomscreen zeigt auf dem großen Bildschirm, was in einer Deiner Roon-Zonen gerade läuft.

- Cover auf unscharfem Hintergrund, dazu Titel, Interpret, Album und Fortschrittsbalken
- Optional synchronisierte Liedtexte von LRCLIB, mit einstellbarem Zeitversatz
- Zonenauswahl; die App folgt einer Zonengruppe, wenn Du in Roon Zonen gruppierst
- Lautstärke-Anzeige für jeden Ausgang der Zone
- Wiedergabe, Pause, Vor und Zurück mit der Fernbedienung [to be filled: "oder per Touch" erst ergänzen, wenn die Touch-Unterstützung (F3b Block 3) auf einem Gerät geprüft ist]
- Einstellungen für Hintergrund (Cover, Interpretenbild oder Farbe), Bildschirmverhalten und mehr
- Deutsch und Englisch

So funktioniert es: Roomscreen spricht direkt mit Deinem Roon Core im selben Netzwerk. Es gibt keinen Hilfsdienst und kein Konto. Beim ersten Start sucht die App Deinen Core (oder Du gibst die Adresse ein). Öffne dann Roon, gehe zu Einstellungen > Erweiterungen, wähle "Roomscreen" und drücke "Aktivieren".

Voraussetzung: ein Roon Core (Roon Server) im selben Netzwerk wie das Gerät.

Kein Konto, keine Werbung, kein Tracking. Roomscreen speichert nur Deine Einstellungen auf dem Gerät. Wenn Du Liedtexte einschaltest, werden Titel, Interpret, Album und Dauer des Stücks an lrclib.net gesendet, um den Text zu finden; schaltest Du Liedtexte in den Einstellungen aus, gibt es keine Anfrage.

Roomscreen ist eine unabhängige App und steht in keiner Verbindung zu Roon Labs und wird von Roon Labs nicht unterstützt. Roon ist eine Marke der Roon Labs LLC.
```

(Die Bezeichnung des Roon-Menüs "Erweiterungen"/"Aktivieren" ist [to be filled: gegen die deutsche Roon-Oberfläche prüfen]; die App-eigene Anleitung nennt "Settings -> Extensions" und "Enable".)

## Schlüsselwörter

läuft gerade, roon, musik, liedtext, cover, albumcover, display, zone, audio

## Marken-Hinweise

- Der Titel enthält kein "Roon"; "Roon" steht nur beschreibend im Text.
- Kein Roon-Logo, keine Aussage, die eine offizielle App nahelegt. Der Hinweis im letzten Absatz bleibt in der Beschreibung.

## Antworten zur Inhaltseinstufung

| Frage | Antwort |
|---|---|
| Gewalt (Zeichentrick / realistisch) | Keine |
| Sexuelle Inhalte / Nacktheit | Keine |
| Vulgäre Sprache | Nicht in der App. Liedtexte stammen von einem Drittdienst (LRCLIB) und können je nach Stück derbe Wörter enthalten; sie sind abschaltbar, die App filtert sie nicht. [Maintainer entscheidet über die Antwort] |
| Alkohol, Tabak, Drogen | Keine |
| Glücksspiel | Keines |
| Nutzergenerierte Inhalte | Keine |
| Soziale Funktionen | Keine |
| Käufe in der App / Werbung | Keine |
| Zielgruppe | Alle Altersgruppen; [to be filled: endgültige Einstufung im Fragebogen des Portals] |

## Exportbestimmungen

Die App nutzt nur die vom System bereitgestellte Standard-Verschlüsselung (HTTPS/TLS), keine eigene oder proprietäre Verschlüsselung. [Maintainer bestätigt im Fragebogen]

## Datenschutz

| Frage | Antwort |
|---|---|
| Personenbezogene Daten? | Nein |
| Konto / Anmeldung | Keine |
| Analyse-, Werbe- oder Tracking-SDKs | Keine |
| Daten, die das Gerät verlassen | Nur bei eingeschalteten Liedtexten: Titel, Interpret, Album, Dauer und User-Agent (App-Name, Version) an lrclib.net |
| Lokales Netzwerk | Kommunikation mit dem eigenen Roon Core |
| Auf dem Gerät gespeichert | Roon-Token, Core-Adresse, gewählte Zone, Anzeigeeinstellungen, Liedtext-Cache (max. 300 Dateien) |
| Weitergabe an Dritte | Keine, außer der Anfrage an lrclib.net |

Text der Datenschutzerklärung: `privacy.md`.
