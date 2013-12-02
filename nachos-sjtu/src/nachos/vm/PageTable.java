package nachos.vm;

import java.util.HashMap;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.TranslationEntry;

public class PageTable {
	private static PageTable instance = null;
	private HashMap<Pair, TranslationEntry> ptMap = new HashMap<Pair, TranslationEntry>();
	private EntryPidPair[] ePairs = new EntryPidPair[Machine.processor()
			.getNumPhysPages()];

	private PageTable() {
	}

	public static PageTable getPageTable() {
		if (instance == null)
			instance = new PageTable();
		return instance;
	}

	public TranslationEntry getEntry(int pid, int vpn) {
		return new TranslationEntry(ptMap.get(new Pair(pid, vpn)));
	}

	public EntryPidPair findKilled() {
		EntryPidPair ep = null;
		do
			ep = ePairs[Lib.random(ePairs.length)];
		while (ep == null || !ep.entry.valid);
		return ep;
	}

	public void setEntry(int pid, TranslationEntry entry) {
		Pair pair = new Pair(pid, entry.vpn);
		TranslationEntry origin = ptMap.get(pair);
		if (origin == null)
			return;

		TranslationEntry subs = new TranslationEntry(entry);

		if (origin.valid)
			ePairs[origin.ppn] = null;
		if (subs.valid)
			ePairs[subs.ppn] = new EntryPidPair(subs, pid);

		ptMap.put(pair, subs);
	}

	public boolean addEntry(int pid, TranslationEntry en) {
		Pair pair = new Pair(pid, en.vpn);
		if (ptMap.containsKey(pair))
			return false;

		TranslationEntry entry = new TranslationEntry(en);
		ptMap.put(pair, entry);
		if (entry.valid)
			ePairs[entry.ppn] = new EntryPidPair(entry, pid);

		return true;
	}

	public TranslationEntry removeEntry(int pid, int vpn) {
		TranslationEntry entry = ptMap.remove(new Pair(pid, vpn));
		if (entry != null || entry.valid)
			ePairs[entry.ppn] = null;
		return entry;
	}

	public void mergeEntry(int pid, TranslationEntry entry) {
		TranslationEntry origin = ptMap.get(new Pair(pid, entry.vpn));
		if (origin == null)
			return;
		TranslationEntry tmp = new TranslationEntry(entry);
		tmp.used = entry.used || origin.used;
		tmp.dirty = entry.dirty || origin.dirty;
		setEntry(pid, tmp);
	}

}