# Store metadata draft (English)

Draft for Amazon Appstore / Live App Testing (LAT). Not submitted. Fields marked "[to be filled]" need the maintainer's own data.

## Listing

| Field | Value |
|---|---|
| App title | Roomscreen |
| Package name | io.github.knoellp.roomscreen (verify against the manifest before submission) |
| Category | Music & Audio (or the closest Fire TV category offered in the Developer Portal) |
| Languages | English (default), German |
| Version in the draft | [to be filled: versionName / versionCode of the submitted build] |
| Support email | <to be filled by the maintainer> |
| Support website | [to be filled] |
| Privacy policy URL | [to be filled: host `privacy.md`] |
| Developer / company name | [to be filled] |

## Short description (max 160 characters)

```
A Now Playing screen for your Roon zones: cover, title, artist, progress and synchronized lyrics on a big, calm display.
```

(120 characters. Check the count again after editing.)

## Long description

```
Roomscreen shows what is playing in one of your Roon zones, on the big screen.

- Cover art on a blurred backdrop, with title, artist, album and a progress bar
- Optional synchronized lyrics from LRCLIB, with an adjustable timing offset
- Zone selection, and it follows a zone group when you group zones in Roon
- Volume panel for every output of the zone
- Play, pause, previous and next with the remote [to be filled: add "or with touch" only after the touch support (F3b Block 3) is verified on a device]
- Settings for the background (cover, artist image or colour), screen behaviour and more
- English and German

How it works: Roomscreen talks directly to your Roon Core in the same network. There is no helper service and no account. On the first start the app finds your Core (or you enter its address). Then open Roon, go to Settings > Extensions, find "Roomscreen" and press Enable.

Requirements: a Roon Core (Roon Server) in the same network as the device.

No account, no ads, no tracking. Roomscreen stores only your settings on the device. If you switch lyrics on, the track title, artist, album and duration are sent to lrclib.net to look up the text; switch lyrics off in the settings and no request is made.

Roomscreen is an independent app and is not affiliated with or endorsed by Roon Labs. Roon is a trademark of Roon Labs LLC.
```

## Keywords

now playing, roon, music, lyrics, cover art, album art, display, zone, clock, audio

(Check the keyword limits of the Developer Portal; "roon" is used descriptively only.)

## Trademark and branding notes

- The title does not contain "Roon". "for Roon" / "Roon" appear only descriptively in the text.
- No Roon logo, no Roon colours or wording that suggests an official app.
- Disclaimer line (last paragraph above) stays in the long description.

## Content rating answers

| Question | Answer |
|---|---|
| Cartoon violence | None |
| Realistic violence | None |
| Sexual content / nudity | None |
| Profanity / crude humour | None in the app. Lyrics come from a third-party service (LRCLIB) and may contain explicit words depending on the track; they are off-able in the settings. [maintainer to decide how to answer; the app does not filter lyrics] |
| Alcohol, tobacco, drugs | None |
| Gambling | None |
| User-generated content | None (the app has no upload or posting) |
| Social features / chat / sharing | None |
| Location sharing | None |
| In-app purchases / ads | None |
| Suggested audience | General (all ages); [to be filled: the final rating is chosen in the portal questionnaire] |

## Export compliance

The app uses only the standard HTTPS/TLS provided by the platform (for lrclib.net lookups and cover images where applicable). It contains no proprietary encryption and no custom cryptographic algorithms. Traffic to the Roon Core on the local network uses the Roon protocol (WebSocket) as defined by Roon. Answer: uses only standard encryption of the operating system; no proprietary or non-standard cryptography. [maintainer to confirm in the portal questionnaire]

## Privacy answers

| Question | Answer |
|---|---|
| Does the app collect personal data? | No |
| Account or sign-in | None |
| Analytics, advertising or tracking SDKs | None; no third-party SDKs for analytics or ads |
| Data sent off the device | Only when lyrics are on: track title, artist, album, duration and a User-Agent (app name and version) to lrclib.net |
| Data on the local network | The app talks to the user's own Roon Core (discovery, WebSocket) |
| Data stored on the device | Roon token, Core address, chosen zone, display settings, a lyrics cache (max 300 files) |
| Data sharing with third parties | None, apart from the lrclib.net lookup above |
| Children | Not directed at children, collects nothing about anyone |
| Permissions | Network access only (see `ManifestTest`); no camera, phone, SMS, location or microphone |

Privacy policy text: `privacy.md`.

## Assets

- Icon 512x512 PNG with transparency: `icon-512.png`
- Icon 1280x720 PNG: `icon-1280x720.png`
- Screenshots 1920x1080: `screenshots/` (to be chosen with the maintainer; 3 to 10)
