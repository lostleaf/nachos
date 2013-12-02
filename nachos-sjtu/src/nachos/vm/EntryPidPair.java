package nachos.vm;

import nachos.machine.TranslationEntry;

public class EntryPidPair {
    public TranslationEntry entry;
	public int pid;

	public EntryPidPair(TranslationEntry entry, int pid) {
		super();
		this.entry = entry;
		this.pid = pid;
	}
}
