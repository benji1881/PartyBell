# PartyBell

Your FFXIV party on your phone.

A Dalamud plugin sends your party list, party changes and party chat to the PartyBell Android app. The app shows your party and a chat feed, and notifies you when the party is full.

The plugin and the app pair with a QR code. Everything between them is encrypted and only works on your own network.

## Install

1. In Dalamud go to Settings, then Experimental
2. Add `https://github.com/benji1881/PartyBell/releases/latest/download/repo.json` as a custom repo, tick it and save
3. Install PartyBell
4. Install `PartyBell-<version>.apk` from the [latest release](https://github.com/benji1881/PartyBell/releases/latest) on your phone
5. Type `/partybell` in game and press Pair a phone
6. When Windows asks, allow the game on private networks only
7. In the app, press Scan pairing code

The plugin updates through Dalamud. The app checks GitHub for a new release and shows a silent notification and an Updates card in Settings. Press Download update and open the downloaded APK to install it.

## Away from home

Install [Tailscale](https://tailscale.com) on your PC and phone, sign in with the same account on both, then pair again.

## License

Apache-2.0. The Plus Jakarta Sans and Fraunces fonts are OFL-1.1.

AI-assisted.
