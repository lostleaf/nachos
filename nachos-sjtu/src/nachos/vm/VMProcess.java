package nachos.vm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import nachos.machine.CoffSection;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;
import nachos.userprog.UserKernel;
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

		for (int i = 0; i < Machine.processor().getTLBSize(); ++i)
			savedTLB[i] = new TranslationEntry(0, 0, false, false, false, false);
	}

	protected boolean allocPages(int vpn, int nPages, boolean readOnly) {
		for (int i = 0; i < nPages; ++i) {
			TranslationEntry t = new TranslationEntry(vpn + i, 0, false,
					readOnly, false, false);
			PageTable.getInstance().add(pid, t);
			Swap.getInstance().add(pid, vpn + i);
			pages.add(new Integer(vpn + i));
		}

		numPages += nPages;

		return true;
	}

	protected TranslationEntry findPageTable(int vpn) {
		return PageTable.getInstance().get(pid, vpn);
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		vmLock.acquire();
		swap(VMKernel.vpn(vaddr));
		TranslationEntry entry = translate(vaddr);
		entry.used = true;
		PageTable.getInstance().set(pid, entry);
		vmLock.release();
		return super.readVirtualMemory(vaddr, data, offset, length);
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		vmLock.acquire();
		swap(VMKernel.vpn(vaddr));

		TranslationEntry entry = translate(vaddr);
		entry.dirty = true;
		PageTable.getInstance().set(pid, entry);

		vmLock.release();
		return super.writeVirtualMemory(vaddr, data, offset, length);
	}

	protected int getFreePage() {
		int ppn = VMKernel.allocPage();

		if (ppn == -1) {
			PidTEPair killed = PageTable.getInstance().getToBeKilled();

			ppn = killed.entry.ppn;
			swapOut(killed.pid, killed.entry.vpn);
		}

		return ppn;
	}

	protected void swapOut(int pid, int vpn) {
		TranslationEntry entry = PageTable.getInstance().get(pid, vpn);

		Processor processor = Machine.processor();
		for (int i = 0; i < processor.getTLBSize(); ++i) {
			TranslationEntry tlbEntry = processor.readTLBEntry(i);
			if (tlbEntry.valid && tlbEntry.vpn == entry.vpn
					&& tlbEntry.ppn == entry.ppn) {
				PageTable.getInstance().merge(pid, tlbEntry);
				entry = PageTable.getInstance().get(pid, vpn);

				tlbEntry.valid = false;
				processor.writeTLBEntry(i, tlbEntry);

				break;
			}
		}

		if (entry.dirty) {
			byte[] memory = Machine.processor().getMemory();
			Swap.getInstance().write(pid, entry.vpn, memory,
					entry.ppn * pageSize);
		}

		entry.valid = false;
		PageTable.getInstance().set(pid, entry);
	}

	protected int swap(int vpn) {
		TranslationEntry entry = findPageTable(vpn);

		if (entry.valid)
			return entry.ppn;

		int ppn = getFreePage();
		swapIn(ppn, vpn);

		return ppn;
	}

	protected void swapIn(int ppn, int vpn) {
		TranslationEntry entry = findPageTable(vpn);

		boolean dirty = false;
		if (lazySec.containsKey(new Integer(vpn))) {
			loadLazySection(vpn, ppn);
			dirty = true;
		} else {
			byte[] page = Swap.getInstance().read(pid, vpn);
			byte[] memory = Machine.processor().getMemory();
			System.arraycopy(page, 0, memory, ppn * pageSize, pageSize);

			dirty = false;
		}

		TranslationEntry subs = new TranslationEntry(entry);
		subs.valid = true;
		subs.ppn = ppn;
		subs.dirty = subs.used = dirty;
		PageTable.getInstance().set(pid, subs);
	}

	protected void loadLazySection(int vpn, int ppn) {
		IPair ip = lazySec.remove(new Integer(vpn));
		if (ip == null)
			return;
		coff.getSection(ip.first).loadPage(ip.second, ppn);
	}

	protected void releaseResource() {
		for (Integer vpn : pages) {
			vmLock.acquire();
			TranslationEntry entry = PageTable.getInstance().remove(pid,
					vpn.intValue());
			if (entry.valid)
				VMKernel.freePage(entry.ppn);

			Swap.getInstance().remove(pid, vpn.intValue());

			vmLock.release();
		}
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		Lib.debug(dbgVM, "save state " + pid);

		Processor processor = Machine.processor();

		for (int i = 0; i < processor.getTLBSize(); ++i) {
			savedTLB[i] = processor.readTLBEntry(i);
			if (savedTLB[i].valid)
				PageTable.getInstance().merge(pid, processor.readTLBEntry(i));
		}

		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Lib.debug(dbgVM, "restore state " + pid);

		Processor processor = Machine.processor();
		for (int i = 0; i < processor.getTLBSize(); ++i) {
			TranslationEntry t = invalidEntry();
			if (savedTLB[i].valid) {
				TranslationEntry entry = findPageTable(savedTLB[i].vpn);
				if (entry != null && entry.valid)
					t = savedTLB[i];
			}
			processor.writeTLBEntry(i, t);
		}
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection coffSection = coff.getSection(s);

			for (int i = 0; i < coffSection.getLength(); i++) {
				int vpn = coffSection.getFirstVPN() + i;

				lazySec.put(new Integer(vpn), new IPair(new Integer(s),
						new Integer(i)));
			}
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
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss:
			vmLock.acquire();
			int vaddr = processor.readRegister(Processor.regBadVAddr);

			if (!handleTLBMiss(vaddr)) {
				vmLock.release();
				finishWith(cause);
			} else
				vmLock.release();

			break;

		default:
			super.handleException(cause);
			break;
		}
	}

	protected boolean handleTLBMiss(int vaddr) {
		TranslationEntry entry = translate(vaddr);
		if (entry == null)
			return false;

		if (!entry.valid) {
			swap(UserKernel.vpn(vaddr));
			entry = translate(vaddr);
		}

		int killed = getTLBToBeKilled();
		killAndSubsTLBEntry(killed, entry);

		return true;
	}

	private TranslationEntry invalidEntry() {
		return new TranslationEntry(0, 0, false, false, false, false);
	}

	protected int getTLBToBeKilled() {
		for (int i = 0; i < Machine.processor().getTLBSize(); ++i)
			if (Machine.processor().readTLBEntry(i).valid == false)
				return i;

		return Lib.random(Machine.processor().getTLBSize());
	}

	protected void killAndSubsTLBEntry(int index, TranslationEntry subs) {
		TranslationEntry entry = Machine.processor().readTLBEntry(index);
		if (entry.valid)
			PageTable.getInstance().merge(pid, entry);

		Machine.processor().writeTLBEntry(index, subs);
	}

	protected Map<Integer, IPair> lazySec = new HashMap<Integer, IPair>();
	protected LinkedList<Integer> pages = new LinkedList<Integer>();
	protected TranslationEntry[] savedTLB = new TranslationEntry[Machine
			.processor().getTLBSize()];

	protected static Lock vmLock = new Lock();
	protected static final int pageSize = Processor.pageSize;
	protected static final char dbgVM = 'v';
}
