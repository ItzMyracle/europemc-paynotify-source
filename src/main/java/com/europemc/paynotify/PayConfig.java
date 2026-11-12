package com.europemc.paynotify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Settings, saved to .minecraft/config/europemc-paynotify.json (editable by hand, via /paynotify or the B menu). */
public class PayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("europemc-paynotify.json");

    // ---- pop-up notifications ----
    /** Pop-up only for incoming payments of at least this much. */
    public double minAmount = 1000.0;
    /** Pop-ups on/off. */
    public boolean enabled = true;
    public boolean playSound = true;
    public boolean debug = false;
    public float durationSeconds = 4.5f;

    // ---- message matching ----
    /** Every EuropeMC payment line starts with this tag. Empty = no requirement. */
    public String prefix = "ECONOMY";
    public String currencySymbol = "$";
    public List<String> incomingKeywords = new ArrayList<>(List.of(
            "received", "paid you", "sent you", "gave you", "deposited", "added to your", "+"));
    public List<String> outgoingKeywords = new ArrayList<>(List.of(
            "you paid", "you sent", "you gave", "you have paid", "you have sent", "paid to", "sent to", "you transferred"));
    /** Regexes, group 1 = player name. */
    public List<String> incomingPlayerPatterns = new ArrayList<>(List.of(
            "(\\.?[A-Za-z_]\\w{1,15})\\s+(?:has\\s+)?(?:paid|sent|gave|transferred)\\s+you",
            "from\\s+(\\.?[A-Za-z_]\\w{1,15})"));
    public List<String> outgoingPlayerPatterns = new ArrayList<>(List.of(
            "you\\s+(?:have\\s+)?(?:paid|sent|gave|transferred)\\s+(\\.?[A-Za-z_]\\w{1,15})",
            "\\bto\\s+(\\.?[A-Za-z_]\\w{1,15})"));

    // ---- gamble tracker ----
    public boolean trackingEnabled = true;
    /** Start a new session automatically when a payment arrives and none is running (always true for the very first session). */
    public boolean autoStartSession = false;
    /** HOST = players send YOU bets and you pay winners. PLAYER = you send the bets and get paid when you win. */
    public String role = "HOST";
    public double betMin = 1000.0;
    /** 0 = no maximum */
    public double betMax = 0.0;
    /** A payout counts for a bet if it comes within this many seconds. */
    public int payoutWindowSeconds = 45;
    public boolean flagAlerts = true;
    public boolean showCornerHud = true;
    public boolean showHotbarBar = true;

    // ---- clickable chat buttons under incoming payments ----
    public boolean chatButtons = true;
    /** true = clicking sends the command right away, false = puts it into your chat box so you can press Enter. */
    public boolean buttonsRunCommand = true;
    public String payCommand = "/pay";
    public String balCommand = "/bal";
    /** "payback" = same amount back, "bal" = /bal <player>, "2x" = double, "0.5x" = half ... */
    public List<String> buttons = new ArrayList<>(List.of("0.5x", "payback", "1.5x", "2x", "3x", "4x", "bal"));
    /** Only treat a line as a payment if it STARTS like one ("Name paid you ..." / "You paid ..."). Stops fake chat messages. */
    public boolean strictStart = true;

    public boolean isHost() { return !"PLAYER".equalsIgnoreCase(role); }

    public static PayConfig load() {
        try {
            if (Files.exists(FILE)) {
                PayConfig c = GSON.fromJson(Files.readString(FILE), PayConfig.class);
                if (c != null) {
                    if (c.prefix == null || c.prefix.isBlank()) c.prefix = "ECONOMY"; // EuropeMC lines carry the ECONOMY tag
                    c.save();
                    return c;
                }
            }
        } catch (Exception e) {
            PayNotifyMod.LOGGER.warn("Could not read config, using defaults", e);
        }
        PayConfig c = new PayConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception e) {
            PayNotifyMod.LOGGER.warn("Could not save config", e);
        }
    }
}
