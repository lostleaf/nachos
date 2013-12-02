package nachos.vm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.threads.ThreadedKernel;

public class Swap {

	private Swap(String swapName) {
		this.swapName = swapName;
		swapFile = VMKernel.fileSystem.open(swapName, true);
	}

	public static Swap getSwap() {
		if (instance == null)
			instance = new Swap("SWAP");
		return instance;
	}

	public int write(int pid, int vpn, byte[] page, int offset) {
		Pair pair = new Pair(pid, vpn);
		int pos = -1;
		if (unAllocSet.contains(pair)) {
			unAllocSet.remove(pair);

			if (queue.size() == 0)
				queue.add(swapMap.size());

			pos = queue.poll();
			swapMap.put(pair, pos);
		} else
			pos = findEntry(pid, vpn);

		if (pos == -1)
			return 0;

		swapFile.write(pos * pageSize, page, offset, pageSize);
		return pos;
	}

	public void close() {
		if (swapFile == null)
			return;

		swapFile.close();
		VMKernel.fileSystem.remove(swapName);
		swapFile = null;
	}

	private int findEntry(int pid, int vpn) {
		Integer ret = swapMap.get(new Pair(pid, vpn));
		return ret == null ? -1 : ret;
	}

	public byte[] read(int pid, int vpn) {
		int pos = findEntry(pid, vpn);
		byte[] ret = new byte[pageSize];
		if (pos == -1)
			return ret;

		if (swapFile.read(pos * pageSize, ret, 0, pageSize) == -1)
			return new byte[pageSize];
		return ret;
	}

	public void add(int pid, int vpn) {
		unAllocSet.add(new Pair(pid, vpn));
	}

	public void remove(int pid, int vpn) {
		if (findEntry(pid, vpn) != -1)
			queue.add(swapMap.remove(new Pair(pid, vpn)));
	}

	private static Swap instance = null;
	private OpenFile swapFile;
	private final int pageSize = Processor.pageSize;
	private String swapName;
	private HashMap<Pair, Integer> swapMap = new HashMap<Pair, Integer>();
	private HashSet<Pair> unAllocSet = new HashSet<Pair>();
	private LinkedList<Integer> queue = new LinkedList<Integer>();

}
