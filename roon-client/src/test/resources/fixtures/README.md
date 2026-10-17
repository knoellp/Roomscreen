# Fixtures

Recorded from a real Roon Core 2.73 with `roon-cli --record` (tokens redacted, LAN address replaced):
- `info.json`: reply body of `registry:1/info`
- `registered.json`: body of the `CONTINUE Registered` message (token replaced by FIXTURE-TOKEN)
- `subscribed.json`: body of `CONTINUE Subscribed` (zones reduced to three: a stopped zone without
  now_playing, two paused zones with tracks)

Derived (zone objects copied from `subscribed.json` and edited, because the test zone could not be
played during recording):
- `changed_track.json`, `changed_pause.json`, `changed_nocover.json`: `zones_changed` bodies
- `changed_seek.json`: `zones_seek_changed` body in the shape documented by node-roon-api-transport

Replace the derived files with recordings once available.
