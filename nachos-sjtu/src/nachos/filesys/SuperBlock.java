package nachos.filesys;

import nachos.machine.Lib;

public class SuperBlock {

	private SuperBlock() {
	}

	public static void create(int rootDir, int freeList) {
		byte[] data = new byte[DiskUtils.BLOCK_SIZE];

		Lib.bytesFromInt(data, 0, LABEL);
		Lib.bytesFromInt(data, 4, rootDir);
		Lib.bytesFromInt(data, 8, freeList);

		DiskUtils.getInstance().writeBlock(0, 1, data);
	}

	public static SuperBlock load() {
		byte[] data = new byte[DiskUtils.BLOCK_SIZE];
		DiskUtils.getInstance().readBlock(0, 1, data);

		if (LABEL != Lib.bytesToInt(data, 0))
			return null;

		SuperBlock ret = new SuperBlock();
		ret.rootDir = Lib.bytesToInt(data, 4);
		ret.freeList = Lib.bytesToInt(data, 8);

		return ret;
	}

	private static final int LABEL = 0xabcdabcd;

	public int rootDir;
	public int freeList;
}
