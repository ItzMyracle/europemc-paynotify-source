package com.europemc.paynotify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Holds all sessions (saved to config/europemc-paynotify-sessions.json) and caches the stats. */
public final class Tracker {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("europemc-paynotify-sessions.json");

    public static List<Session> sessions = new ArrayList<>();
    private static boolean dirty;
    private static long lastSave;
    private static int version;

    private record Cached(int ver, long at, Stats stats) {}
    private static final Map<String, Cached> CACHE = new HashMap<>();

    private Tracker() {}

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                List<Session> l = GSON.fromJson(Files.readString(FILE), new TypeToken<List<Session>>() {}.getType());
                if (l != null) sessions = l;
            }
        } catch (Exception e) {
            PayNotifyMod.LOGGER.warn("Could not read sessions file", e);
        }
        invalidate();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(sessions));
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (Exception e) {
            PayNotifyMod.LOGGER.warn("Could not save sessions", e);
        }
    }

    public static void tick() {
        if (dirty && System.currentTimeMillis() - lastSave > 5000) save();
    }

    public static void invalidate() { version++; }

    public static Session active() {
        for (int i = sessions.size() - 1; i >= 0; i--) {
            if (sessions.get(i).isActive()) return sessions.get(i);
        }
        return null;
    }

    public static Session startSession(String name) {
        endSession();
        Session s = new Session();
        s.id = UUID.randomUUID().toString();
        s.name = (name == null || name.isBlank()) ? "Session #" + (sessions.size() + 1) : name;
        s.start = System.currentTimeMillis();
        sessions.add(s);
        dirty = true;
        invalidate();
        return s;
    }

    public static void endSession() {
        Session a = active();
        if (a != null) {
            a.end = System.currentTimeMillis();
            dirty = true;
            invalidate();
        }
    }

    public static void delete(Session s) {
        sessions.remove(s);
        dirty = true;
        invalidate();
    }

    /** Records a payment. Returns false if there is no running session (and none was auto-started). */
    public static boolean record(TxParser.Parsed p, PayConfig cfg) {
        Session s = active();
        if (s == null) {
            if (!cfg.autoStartSession && !sessions.isEmpty()) return false;
            s = startSession(null);
        }
        Tx t = new Tx();
        t.time = System.currentTimeMillis();
        t.incoming = p.incoming();
        t.player = p.player();
        t.amount = p.amount();
        s.txs.add(t);
        dirty = true;
        invalidate();
        return true;
    }

    /** "UNDER" / "OVER" if an incoming bet is outside your min/max (only in HOST mode), otherwise null. */
    public static String classify(TxParser.Parsed p, PayConfig cfg) {
        if (!cfg.isHost() || !p.incoming()) return null;
        if (p.amount() < cfg.betMin - 1e-9) return "UNDER";
        if (cfg.betMax > 0 && p.amount() > cfg.betMax + 1e-9) return "OVER";
        return null;
    }

    public static Stats stats(Session s) {
        return cached(s.id, () -> s.txs);
    }

    public static Stats allTime() {
        return cached("ALL", () -> {
            List<Tx> all = new ArrayList<>();
            for (Session s : sessions) all.addAll(s.txs);
            return all;
        });
    }

    private static Stats cached(String key, java.util.function.Supplier<List<Tx>> txs) {
        long now = System.currentTimeMillis();
        Cached c = CACHE.get(key);
        if (c != null && c.ver() == version && now - c.at() < 500) return c.stats();
        Stats st = Stats.compute(txs.get(), PayNotifyMod.config, now);
        CACHE.put(key, new Cached(version, now, st));
        return st;
    }
}
