# PartyBell

Your party on your phone, for Final Fantasy XIV.

A Dalamud plugin sends your party list, party changes and party chat to the PartyBell Android app. The app shows your party and a chat feed, and notifies you when the party is full.

The plugin and the app pair with a QR code. Everything between them is encrypted and only works on your own network.

## Install

1. In Dalamud go to Settings, then Experimental
2. Add `https://github.com/benji1881/PartyBell/releases/latest/download/repo.json` as a custom repo, tick it and save
3. Install PartyBell
4. Install `PartyBell-<version>.apk` from the [latest release](https://github.com/benji1881/PartyBell/releases/latest) on your phone
5. Type `/partybell` in game and press Pair a phone
6. When Windows asks, allow the game on private networks only. On Linux or Mac with a firewall on, allow TCP and UDP port 4747 from your home network
7. In the app, press Scan pairing code

The plugin updates through Dalamud. The app checks GitHub for a new release and shows a silent notification and an Updates card in Settings. Press Download update and open the downloaded APK to install it.

## Staying connected

The app keeps a silent notification while it is connected and reconnects by itself after the phone restarts or the app updates. For alerts to arrive on time with the screen off, press Turn off battery optimisation in the app's Settings and choose Don't optimise for PartyBell.

If your PC gets a new address on your home network, the app finds it again by itself, so you don't have to pair again.

## Away from home

Install [Tailscale](https://tailscale.com) on your PC and phone, sign in with the same account on both, then pair again.

## Privacy

Your party, chat, friend list and Party Finder listings go only from the plugin to your paired phone, encrypted. The app saves the last 1,000 tells with each person on your phone, until you delete the conversation. The plugin saves each phone's key encrypted for your user account, and the app only allows unencrypted network traffic to your PC, whose messages PartyBell encrypts itself. There is no PartyBell server and no analytics. The app contacts GitHub to check for updates, and the QR scanner comes from Google Play services.

## License

Apache-2.0. The Plus Jakarta Sans and Fraunces fonts are OFL-1.1.

PartyBell is not affiliated with or endorsed by Square Enix. Final Fantasy XIV is a trademark of Square Enix.

AI-assisted.
