package nachos.vm;

import java.util.HashMap;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.TranslationEntry;

public class PageTable {

	private PageTable() {

	}

	public static PageTable getInstance() {
		if (instance == null)
			instance = new PageTable();
		return instance;
	}

	public boolean add(int pid, TranslationEntry entry) {
		IPair ip = new IPair(pid, entry.vpn);
		if (pageTable.containsKey(ip))
			return false;

		entry = new TranslationEntry(entry);
		pageTable.put(ip, entry);
		if (entry.valid)
			pageInfo[entry.ppn] = new PidTEPair(entry, pid);

		return true;
	}

	public TranslationEntry remove(int pid, int vpn) {
		TranslationEntry entry = pageTable.remove(new IPair(pid, vpn));

		if (entry != null && entry.valid)
			pageInfo[entry.ppn] = null;

		return entry;
	}

	private TranslationEntry mergeInfo(TranslationEntry entry1,
			TranslationEntry entry2) {
		TranslationEntry ret = new TranslationEntry(entry1);

		ret.used = (entry1.used || entry2.used);
		ret.dirty = (entry1.dirty || entry2.dirty);
		return ret;
	}

	public PidTEPair getToBeKilled() {
		PidTEPair ret = null;
		do {
			int index = Lib.random(pageInfo.length);
			ret = pageInfo[index];
		} while (ret == null || ret.entry.valid == false);

		return ret;
	}

	public TranslationEntry get(int pid, int vpn) {
		IPair iPair = new IPair(pid, vpn);
		if (!pageTable.containsKey(iPair))
			return null;
		return new TranslationEntry(pageTable.get(iPair));
	}

	public void set(int pid, TranslationEntry entry) {
		setOrMerge(pid, entry, false);
	}

	public void merge(int pid, TranslationEntry entry) {
		setOrMerge(pid, entry, true);
	}

	private void setOrMerge(int pid, TranslationEntry entry, boolean isMerge) {
		IPair pvPair = new IPair(pid, entry.vpn);
		if (!pageTable.containsKey(pvPair))
			return;

		TranslationEntry last = pageTable.get(pvPair);
		TranslationEntry subs = isMerge ? new TranslationEntry(mergeInfo(entry,
				last)) : new TranslationEntry(entry);

		if (last.valid)
			pageInfo[last.ppn] = null;

		if (entry.valid)
			pageInfo[entry.ppn] = new PidTEPair(subs, pid);

		pageTable.put(pvPair, subs);
	}

	private static PageTable instance = null;

	private HashMap<IPair, TranslationEntry> pageTable = new HashMap<IPair, TranslationEntry>();
	private PidTEPair[] pageInfo = new PidTEPair[Machine.processor()
			.getNumPhysPages()];

	protected static final char dbgVM = 'v';
}
