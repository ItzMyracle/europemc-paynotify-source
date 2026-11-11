package com.europemc.paynotify;

/** One payment: money you received (incoming) or sent (outgoing). */
public class Tx {
    public long time;
    public boolean incoming;
    public String player = "Unknown";
    public double amount;
}
