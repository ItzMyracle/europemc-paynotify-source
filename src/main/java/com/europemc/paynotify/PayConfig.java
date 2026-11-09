package com.europemc.paynotify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Settings, saved to .minecraft/config/europemc-paynotify.json (editable by hand or via /paynotify). */
public class PayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("europemc-paynotify.json");

    /** Only notify when the payment is at least this much. */
    public double minAmount = 1000.0;
    public boolean enabled = true;
    public boolean playSound = true;
    /** Prints the raw ECONOMY messages in your chat so you can see exactly what the server sends. */
    public boolean debug = false;
    /** How long the pop-up stays on screen. */
    public float durationSeconds = 4.5f;
    /** A message must contain this text to be checked. */
    public String prefix = "ECONOMY";
    public String currencySymbol = "$";
    /** Message must contain one of these (case-insensitive) to count as INCOMING money. Empty list = any ECONOMY message. */
    public List<String> incomingKeywords = new ArrayList<>(List.of(
            "received", "paid you", "sent you", "gave you", "deposited", "added to your", "+"));

    public static PayConfig load() {
        try {
            if (Files.exists(FILE)) {
                PayConfig c = GSON.fromJson(Files.readString(FILE), PayConfig.class);
                if (c != null) return c;
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
