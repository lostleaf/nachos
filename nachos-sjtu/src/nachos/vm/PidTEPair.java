package nachos.vm;

import nachos.machine.TranslationEntry;

public class PidTEPair {
    public TranslationEntry entry;
    public int pid;

    public PidTEPair(TranslationEntry entry, int pid) {
        this.entry = entry;
        this.pid = pid;
    }
}
