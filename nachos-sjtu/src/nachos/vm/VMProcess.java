package nachos.vm;

import java.util.HashMap;
import java.util.LinkedList;

import nachos.machine.CoffSection;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;
import nachos.userprog.UserProcess;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();

		vmLock = new Lock();
		lazySections = new HashMap<Integer, IntPair>();
		pages = new LinkedList<Integer>();

		savedTLB = new TranslationEntry[Machine.processor().getTLBSize()];
		for (int i = 0; i < Machine.processor().getTLBSize(); i++)
			savedTLB[i] = new TranslationEntry(0, 0, false, false, false, false);

	}

	@Override
	protected TranslationEntry findPageTable(int vpn) {
		return PageTable.getPageTable().getEntry(pid, vpn);
	}

	protected void loadLazySection(int vpn, int ppn) {
		Lib.debug(dbgVM, "\tload lazy section to virtual page " + vpn
				+ " on phys page " + ppn);
		IntPair pair = lazySections.remove(vpn);
		if (pair == null)
			return;

		CoffSection section = coff.getSection(pair.int1);
		coff.getSection(pair.int1).loadPage(pair.int2, ppn);
	}

	protected void swapIn(int ppn, int vpn) {
        Lib.debug(dbgVM, "\tswapping in virtual page " + vpn + " to phys page " + ppn);

        TranslationEntry entry = findPageTable(vpn);
        Lib.assertTrue(entry != null, "Target doesn't exist in page table");
        Lib.assertTrue(!entry.valid, "Target entry is valid");

        boolean dirty = false;
        if (lazySections.containsKey(new Integer(vpn))) {
            loadLazySection(vpn, ppn);
            dirty = true;
        } else {
            byte[] page = Swap.getSwap().readFromSwapfile(pid, vpn);
            byte[] memory = Machine.processor().getMemory();
            System.arraycopy(page, 0, memory, ppn * pageSize, pageSize);

            dirty = false;
        }

        TranslationEntry supplant = new TranslationEntry(entry);
        supplant.valid = true;
        supplant.ppn = ppn;
        supplant.used = dirty;
        supplant.dirty = dirty;
        PageTable.getInstance().setEntry(pid, supplant);
	}

	protected int swap(int vpn) {
		TranslationEntry entry = findPageTable(vpn);
		Lib.assertTrue(entry != null, "page " + vpn + " not in PageTable");

		if (entry.valid)
			return entry.ppn;

		int ppn = findFreePage();
		swapIn(ppn, vpn);

		return ppn;
	}

	@Override
	protected void beforeRW(int vpn, boolean isRead) {
		vmLock.acquire();

		swap(vpn);
		TranslationEntry entry = findPageTable(vpn);

		if (isRead)
			entry.used = true;
		else
			entry.dirty = true;

		PageTable.getPageTable().setEntry(pid, entry);
		vmLock.release();
	}

	protected int findFreePage() {
		int ppn = VMKernel.allocPage();
		if (ppn == -1) {
			TranslationEntryWithPid victim = PageTable.getPageTable().pickVictim();
			ppn = victim.entry.ppn;
			swapOut(victim.pid, victim.entry.vpn);

		}
		return ppn;
	}
	protected void swapOut(int pid, int vpn){
        TranslationEntry entry = PageTable.getInstance().getEntry(pid, vpn);

		// kill the to be killed entry
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
			TranslationEntry tlbEntry = Machine.processor().readTLBEntry(i);

			if (tlbEntry.valid && tlbEntry.vpn == entry.vpn
					&& tlbEntry.ppn == entry.ppn) {
				PageTable.getPageTable().combineEntry(pid, tlbEntry);
				entry = PageTable.getPageTable().getEntry(pid, entry.vpn);
				tlbEntry.valid = false;
				Machine.processor().writeTLBEntry(i, tlbEntry);
				break;
			}
		}

		if (entry.dirty) {
			byte[] memory = Machine.processor().getMemory();
			Swap.getSwap().writeToSwapfile(pid, entry.vpn, memory,
					entry.ppn * pageSize);
		}

		entry.valid = false;
		PageTable.getPageTable().setEntry(pid, entry);
	}
	@Override
	protected boolean allocPages(int vpn, int requestPages, boolean readOnly) {
		for (int i = 0; i < requestPages; i++) {
			PageTable.getPageTable().addEntry(
					pid,
					new TranslationEntry(vpn + i, 0, false, readOnly, false,
							false));
			Swap.getSwap().addEntry(pid, vpn + i);
			pages.add(new Integer(vpn + i));
		}
		numPages += requestPages;
		return true;
	}

	@Override
	protected void releaseResource() {
		for (int vpn : pages) {
			vmLock.acquire();

			TranslationEntry entry = PageTable.getPageTable().removeEntry(pid,
					vpn);
			if (entry.valid)
				VMKernel.freePage(entry.ppn);
			Swap.getSwap().removeEntry(pid, vpn);

			vmLock.release();
		}
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		// Lib.debug(dbgVM, "save state--save TLB pid " + pid);

		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
			savedTLB[i] = Machine.processor().readTLBEntry(i);
			if (savedTLB[i].valid)
				PageTable.getPageTable().combineEntry(pid, savedTLB[i]);
		}

		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		// Lib.debug(dbgVM, "restore state--restore TLB; pid:" + pid);

		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
			TranslationEntry saved = Machine.processor().readTLBEntry(i);
			boolean flag = false;
			if (saved.valid) {
				TranslationEntry t = findPageTable(saved.vpn);
				flag = t != null && t.valid;
			}
			TranslationEntry subs = flag ? saved : new TranslationEntry(0, 0,
					false, false, false, false);
			Machine.processor().writeTLBEntry(i, subs);
		}
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		for (int j = 0; j < coff.getNumSections(); j++) {
			CoffSection cSection = coff.getSection(j);

			for (int i = 0; i < cSection.getLength(); i++)
				lazySections.put(cSection.getFirstVPN() + i, new IntPair(j, i));
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		if (cause != Processor.exceptionTLBMiss) {
			super.handleException(cause);
			return;
		}

		vmLock.acquire();
		int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);

		if (!handleTLBMiss(vaddr)) {
			Lib.debug(dbgVM, "Page fault!! handle TLB miss failed. bad address"
					+ vaddr);
			vmLock.release();
			endUpWith(cause);
			return;
		}

		vmLock.release();
	}

	private boolean handleTLBMiss(int vaddr) {
		int vpn = vaddr / pageSize;
		TranslationEntry entry = findPageTable(vpn);
		if (entry == null)
			return false;

		if (!entry.valid) {
			swap(vpn);
			entry = findPageTable(vpn);
		}

		// find an entry in TLB to substitute
		int subsIndex = -1;
		for (int i = 0; i < Machine.processor().getTLBSize(); ++i)
			if (Machine.processor().readTLBEntry(i).valid == false) {
				subsIndex = i;
				break;
			}
		if (subsIndex == -1)
			subsIndex = Lib.random(Machine.processor().getTLBSize());

		// do substitute
		TranslationEntry tlbEntry = Machine.processor().readTLBEntry(subsIndex);
		if (tlbEntry.valid)
			PageTable.getPageTable().combineEntry(pid, tlbEntry);

		Machine.processor().writeTLBEntry(subsIndex, entry);

		return true;
	}

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final char dbgVM = 'v';
	private Lock vmLock;

	protected HashMap<Integer, IntPair> lazySections = null;
	protected LinkedList<Integer> pages = null;
	protected TranslationEntry[] savedTLB = null;

}
