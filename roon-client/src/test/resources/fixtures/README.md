# Fixtures

Recorded from a real Roon Core 2.73 with `roon-cli --record` (tokens redacted, LAN address replaced):
- `info.json`, `registered.json` (token replaced by FIXTURE-TOKEN), `subscribed.json` (3 zones: stopped
  "Buero" without now_playing, two paused zones with tracks)
- `changed_play.json`: Buero paused -> playing
- `changed_loading.json`: next track: state `loading`, no seek_position, plus a `zones_seek_changed` entry with `seek_position: null`
- `changed_track.json`: loading -> playing, new title
- `changed_pause.json`, `changed_resume.json`: pause and resume
- `changed_seek.json`: a `zones_seek_changed` message (the Core sends these about once per second while playing)

Derived: `changed_nocover.json` is `changed_pause.json` with `image_key` removed (no real zone without cover was available).

Not recorded: the queue running out (zone reaching stopped after playing).
All zone messages refer to the Buero zone id.

Group/ungroup recording (real Core, user grouped Kamin + Fernseher, then ungrouped):
- `subscribed_six.json`: Subscribed with all six zones
- `group_added.json`: both member ids removed, new group zone "Kamin + 1" (new zone_id) added, in one message
- `group_removed.json`: group id removed, both members added back with their original ids, in one message
- `group_member_changed.json`: a later `zones_changed` for Kamin
