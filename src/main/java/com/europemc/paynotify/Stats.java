package com.europemc.paynotify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** All numbers shown in the GUI/HUD, computed from a list of payments. */
public final class Stats {
    public double in, out, profit, refunded, unmatchedPayouts;
    public int bets, roundsFinal, wins, losses, pushes, pending;
    /** > 0 = current win streak, < 0 = current losing streak */
    public int curStreak, bestWin, worstLoss;
    public double biggestWin, biggestLoss;
    public final List<Flag> flags = new ArrayList<>();
    public final List<TxView> views = new ArrayList<>();
    /** +1 win, -1 loss, 0 push, oldest first */
    public final List<Integer> form = new ArrayList<>();

    public static final class Flag {
        public Tx tx;
        public boolean under;
        public double limit;
        public boolean refunded;
    }

    public record TxView(Tx tx, String tag) {}

    public double winRate() {
        int decided = wins + losses;
        return decided == 0 ? 0 : (wins * 100.0) / decided;
    }

    public int openFlags() {
        int n = 0;
        for (Flag f : flags) if (!f.refunded) n++;
        return n;
    }

    private static final class Round {
        Tx bet;
        double payout;
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    public static Stats compute(List<Tx> txs, PayConfig cfg, long now) {
        Stats s = new Stats();
        List<Tx> sorted = new ArrayList<>(txs);
        sorted.sort(Comparator.comparingLong(t -> t.time));

        boolean host = cfg.isHost();
        long window = Math.max(1, cfg.payoutWindowSeconds) * 1000L;
        long refundWindow = 30L * 60L * 1000L;
        List<Round> rounds = new ArrayList<>();

        for (Tx t : sorted) {
            if (t.incoming) s.in += t.amount; else s.out += t.amount;
            boolean betDir = host ? t.incoming : !t.incoming;

            if (betDir) {
                if (host) {
                    boolean under = t.amount < cfg.betMin - 1e-9;
                    boolean over = cfg.betMax > 0 && t.amount > cfg.betMax + 1e-9;
                    if (under || over) {
                        Flag f = new Flag();
                        f.tx = t;
                        f.under = under;
                        f.limit = under ? cfg.betMin : cfg.betMax;
                        s.flags.add(f);
                        s.views.add(new TxView(t, under ? "UNDER" : "OVER"));
                        continue;
                    }
                }
                s.bets++;
                Round r = new Round();
                r.bet = t;
                rounds.add(r);
                s.views.add(new TxView(t, "BET"));
            } else {
                if (host) {
                    Flag match = null;
                    for (int i = s.flags.size() - 1; i >= 0; i--) {
                        Flag f = s.flags.get(i);
                        if (t.time - f.tx.time > refundWindow) break;
                        if (!f.refunded && same(f.tx.player, t.player)
                                && Math.abs(f.tx.amount - t.amount) < 0.5 && f.tx.time <= t.time) {
                            match = f;
                            break;
                        }
                    }
                    if (match != null) {
                        match.refunded = true;
                        s.refunded += t.amount;
                        s.views.add(new TxView(t, "REFUND"));
                        continue;
                    }
                }
                Round match = null;
                for (int i = rounds.size() - 1; i >= 0; i--) {
                    Round r = rounds.get(i);
                    if (t.time - r.bet.time > window) break;
                    if (same(r.bet.player, t.player) && t.time >= r.bet.time) { match = r; break; }
                }
                if (match != null) match.payout += t.amount; else s.unmatchedPayouts += t.amount;
                s.views.add(new TxView(t, "PAYOUT"));
            }
        }

        int cur = 0;
        for (Round r : rounds) {
            if (now - r.bet.time < window) { s.pending++; continue; }
            double net = host ? r.bet.amount - r.payout : r.payout - r.bet.amount;
            s.roundsFinal++;
            if (net > 0.0001) {
                s.wins++;
                cur = cur > 0 ? cur + 1 : 1;
                s.biggestWin = Math.max(s.biggestWin, net);
                s.form.add(1);
            } else if (net < -0.0001) {
                s.losses++;
                cur = cur < 0 ? cur - 1 : -1;
                s.biggestLoss = Math.max(s.biggestLoss, -net);
                s.form.add(-1);
            } else {
                s.pushes++;
                s.form.add(0);
                continue;
            }
            s.bestWin = Math.max(s.bestWin, cur);
            s.worstLoss = Math.max(s.worstLoss, -cur);
        }
        s.curStreak = cur;
        s.profit = s.in - s.out;
        return s;
    }
}
