# EuropeMC Pay Notifier (Fabric 1.21.11, client-side)

Shows a pop-up toast (top-right) whenever an `ECONOMY` chat message says you received
at least your chosen amount.

## Change the amount any time (in game)
- `/paynotify min 5000`   -> only notify for payments of 5,000 or more
- `/paynotify toggle`     -> on/off
- `/paynotify sound false`
- `/paynotify test`       -> preview the pop-up
- `/paynotify debug true` -> prints raw ECONOMY messages in chat (use this if nothing triggers)

Or edit `.minecraft/config/europemc-paynotify.json` and run `/paynotify reload`.
Fields: minAmount, enabled, playSound, prefix, currencySymbol, incomingKeywords.

## Build
1. Install JDK 21 and Gradle 9.2+ (or copy `src/`, `build.gradle`, `gradle.properties` into the official
   Fabric example mod template: https://github.com/FabricMC/fabric-example-mod).
2. Run `gradle wrapper` once, then `./gradlew build`.
3. Jar is in `build/libs/` -> put it in your `mods` folder with Fabric Loader + Fabric API.

## No-install build (GitHub)
Upload this project to a new GitHub repository. The included workflow builds the jar automatically:
Actions tab -> latest run -> Artifacts -> europemc-paynotify-jar.
