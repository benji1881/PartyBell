# PartyBell

Your FFXIV party on your phone.

A Dalamud plugin sends your party list, party changes and party chat to the PartyBell Android app. The app shows your party and a chat feed, and notifies you when the party is full.

You can write in party and alliance chat from the app or straight from a notification. Commands and emoji can't be sent from the phone. You can turn this off in `/partybell`.

Ready checks show up as a notification with Ready and Not ready buttons that answer in game. You can turn this off in `/partybell` too. When a duty pops, you get a notification with a countdown, but you accept it in game.

If the app loses the connection to your PC for a minute, it shows a silent notification. Closing the game doesn't trigger it.

Tells and Free Company chat have their own tab. Tells look like direct messages, and you can answer them from the app or from a notification. Conversations can be pinned, muted or deleted on the phone. Tells notify you by default, Free Company chat doesn't.

The Friends tab shows who is online, lets you send them a tell and invite them to your party. If it stays empty, open your friend list in game once. Invites can be turned off in `/partybell`.

The party chat clears when you join a new party, and all chats clear when you log out or change character. Job icons are read from your own game install.

The plugin and the app pair with a QR code. Everything between them is encrypted and only works on your own network.

## Install

1. In Dalamud go to Settings, then Experimental
2. Add `https://github.com/benji1881/PartyBell/releases/latest/download/repo.json` as a custom repo, tick it and save
3. Install PartyBell
4. Install `PartyBell-<version>.apk` from the [latest release](https://github.com/benji1881/PartyBell/releases/latest) on your phone
5. Type `/partybell` in game and press Pair a phone
6. In the app, press Scan pairing code
7. When Windows asks, allow PartyBell on private networks only

The plugin updates through Dalamud. The app checks GitHub for a new release and shows a silent notification and an Updates card in Settings. Press Install update and allow PartyBell to install apps the first time.

## Away from home

Install [Tailscale](https://tailscale.com) on your PC and phone, sign in with the same account on both, then pair again.

## License

Apache-2.0. The Plus Jakarta Sans and Fraunces fonts are OFL-1.1.

AI-assisted.
