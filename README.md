# EuropeMC Pay Notifier + Gamble Tracker (Fabric 1.21.11, client-side)

## Press B  ->  Gamble Tracker
* Sessions: create, end, delete, rename. Pick one in the sidebar or "All-Time".
* Overview: profit, money in/out, W/L, win rate, biggest win/loss, session streak + all-time streak, form strip.
* Transactions: every payment (IN/OUT) with player, amount and tag.
* Flagged: bets under your minimum / over your maximum. Click a row to copy `/pay <player> <amount>` for the refund.
* Settings: min bet, max bet, role (HOST/PLAYER), pop-up threshold, payout window, HUD toggles.
* HUD: corner panel (top right) and a bar above the hotbar. Both can be switched off in Settings.

## Chat buttons
Every incoming EuropeMC payment line ("ECONOMY ... Name paid you $ 470M") gets clickable buttons appended:
`[0.5x] [payback] [1.5x] [2x] [3x] [4x] [bal]`
* `[2x]` runs `/pay <Name> <amount*2>` (digits, e.g. 2000000000 -> 4000000000)
* `[payback]` pays the same amount back
* `[bal]` runs `/bal <Name>`
Change the list in `config/europemc-paynotify.json` (`buttons`, `payCommand`, `balCommand`).
In Settings (B) you can switch the buttons off, or make a click only fill the chat box instead of sending.

## Commands
`/paynotify min|betmin|betmax <250k | 5M | 1B | 1T>` , `toggle`, `sound <true/false>`, `debug <true/false>`, `reload`, `test [amount]`

## If payments are not detected
Run `/paynotify debug true`, get paid / pay someone, and look at the printed line. Then edit
`config/europemc-paynotify.json` (incomingKeywords, outgoingKeywords, incomingPlayerPatterns, outgoingPlayerPatterns).

## Build
Upload the project to GitHub; the included workflow builds the jar (Actions -> Artifacts).
