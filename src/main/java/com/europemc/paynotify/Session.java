package com.europemc.paynotify;

import java.util.ArrayList;
import java.util.List;

public class Session {
    public String id;
    public String name;
    public long start;
    /** 0 = still running */
    public long end;
    public List<Tx> txs = new ArrayList<>();

    public boolean isActive() { return end == 0; }
}
