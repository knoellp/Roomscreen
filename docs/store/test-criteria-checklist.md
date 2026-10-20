# Amazon Test Criteria checklist (LAT preparation)

Status as of this draft (HEAD 53666a8 plus the store files). Nothing is submitted.

## Source of the criteria texts

Only criteria that `docs/PLAN-F3b.md` quotes (from the Amazon Test Criteria page, version of 18.09.2026, https://developer.amazon.com/docs/app-testing/test-criteria) are listed with their text. Other criterion numbers, and the exact numbering of groups 2a, 3 and 4 beyond these, were **not** verified and are deliberately not listed. Before submission, go through the current Amazon page line by line and add rows.

Criteria quoted in the brief:

| No. | Text as given in the brief |
|---|---|
| Group 1 | No permissions Amazon does not want: camera, phone, SMS, location, microphone |
| 2.3 | A hint must appear at the latest after 15 s (while loading / connecting) |
| 2.8 | Fire TV apps are landscape |
| 2.12 | Touch elements must recede with controller input (no touch-only elements when only a remote is connected) |
| 3.8 | An update must keep the app data |
| 3.10 | Touch targets: at least 7 x 7 mm (Amazon); 48 dp (Android guideline) |
| 3.15 | Icon 512 x 512 PNG with transparency |
| 4.2 | `onPause` / `onResume` must work on voice search (Alexa overlays) |

## Status

| Criterion | Status | Evidence |
|---|---|---|
| Group 1: no unwanted permissions | met | `ManifestTest.requestsNoPermissionAmazonDislikes`; manifest requests INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE only (Core discovery) |
| 2.3: hint within 15 s of connecting | open | The connecting screen has a spinner from the first frame (`STATUS.md`, device photo `p5-01-connecting.png`; Roborazzi `1-connecting`), but the detailed "Core not reachable" hint appears after 20 s (`STATUS.md`). Whether the brief's 15 s limit applies to the spinner or to the text must be checked on the Amazon page; if the text counts, the 20 s must be lowered |
| 2.8: landscape on Fire TV | open | Fire TV devices render landscape; the manifest sets no `screenOrientation` lock, `configChanges` includes `orientation|screenSize`, and `ConfigChangeTest` shows that a configuration change keeps the activity, view model and screen. Portrait on the Echo Show 15 is open (point 2 below) |
| 2.12: touch recedes with remote input | open until F3b Block 3 | The plan uses one control bar for both input types (no separate touch elements). Touch support is not implemented yet; verify with the touch flows on the device |
| 3.8: update keeps data | open (planned) | Update test 1.0.0 to 1.1.0 on the stick (`PLAN-F3b.md` 4.5, Block 6): token, zone and settings stay, no new Enable. Same signing key (`RELEASING.md`, fingerprint `config/release-fingerprint.txt`) |
| 3.10: touch target size | open until Block 3 device run | Control bar buttons at least 48 dp and 7 x 7 mm on the target devices; to be measured with the touch tests |
| 3.15: icon 512 x 512 PNG with transparency | met | `icon-512.png` is 512 x 512, 8-bit RGBA, rounded corners transparent (7595 fully transparent pixels), rendered by `StoreAssetsTest` |
| 4.2: `onPause` / `onResume` on voice search | open until Block 5 device run | Lifecycle tests: `STATUS.md` row "Lifecycle (Home key, Activity restart)" (no double connection). The voice-search overlay run (`scripts/f3b-lifecycle.sh`) is not done yet |
| Icon 1280 x 720 | met | `icon-1280x720.png`, 1280 x 720 px |
| Screenshots 1920 x 1080, 3 to 10 | open | `docs/store/screenshots/` not filled yet (to be chosen with the maintainer) |

## Notes for the Live App Testing run

- The tester account (the Amazon account the Echo Show is signed in with) must be able to receive LAT e-mails. Amazon sends the invitation by e-mail and expects it to be opened on a computer; the app is then installed through "Get App" on the test page (Amazon "Prepare App Testers").
- Whether the invitation requires a marketing opt-in is only stated in a third-party guide, not by Amazon: open, check on the first attempt.
- Whether the Echo Show 15 appears in the device selection of the Developer Portal, and whether LAT builds can be installed there: open, check in F5.

## Open technical points (`PLAN-F3b.md` section 9)

1. Density class and dp sizes Fire OS reports on the Echo Show 15.
2. Portrait in the Fire TV mode of the Echo Show 15.
3. Fallback to the Alexa home screen despite the wake lock.
4. Echo Show 15 in the Developer Portal device selection; LAT installation there.
5. Menu path to "Apps from unknown sources" on the AFTSS with current firmware.
6. Marketing opt-in as a prerequisite for LAT invitations.
7. Key codes of the Fire TV remote on the Echo Show (via the diagnostics overlay in F5).
