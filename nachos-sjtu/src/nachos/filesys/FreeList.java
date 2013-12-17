package nachos.filesys;

import java.util.Arrays;

/**
 * FreeList is a single special file used to manage free space of the
 * filesystem. It maintains a list of sector numbers to indicate those that are
 * available to use. When there's a need to allocate a new sector in the
 * filesystem, call allocate(). And you should call deallocate() to free space
 * at a appropriate time (eg. when a file is deleted) for reuse in the future.
 * 
 * @author starforever
 */
public class FreeList {

	private FreeList() {
		dirty = false;
	}

	private FreeList(int block, byte[] data) {
		this.block = block;
		this.data = data;
	}

	public static FreeList create(int block, int head) {
		int count = getBlocksCount();
		FreeList list = new FreeList(block, new byte[count
				* DiskUtils.BLOCK_SIZE]);

		Arrays.fill(list.data, (byte) 0);
		int nBlocks = DiskUtils.DISK_SIZE / DiskUtils.BLOCK_SIZE;

		for (int i = 0; i < head; ++i)
			list.setUse(i);
		for (int i = block; i < block + count; ++i)
			list.setUse(i);
		for (int i = nBlocks; i < list.getLeavesNum(); ++i)
			list.setUse(i);

		list.save();
		return list;
	}

	/** allocate a new sector in the disk */
	public int allocate() {
		int r = getFree();
		dirty = true;
		setUse(r);
		return r;
	}

	/** deallocate a sector to be reused */
	public void deallocate(int i) {
		resetUse(i);
		dirty = true;
	}

	/** load the content of freelist from the disk */
	public static FreeList load(int block) {
		FreeList list = new FreeList(block, new byte[DiskUtils.BLOCK_SIZE
				* getBlocksCount()]);

		DiskUtils.getInstance().readBlock(block, FreeList.getBlocksCount(),
				list.data);

		return list;
	}

	/** save the content of freelist to the disk */
	public void save() {
		if (!dirty)
			return;

		DiskUtils.getInstance().writeBlock(block,
				data.length / DiskUtils.BLOCK_SIZE, data);
		dirty = false;
	}

	public static int getBlocksCount() {
		return 2;
	}

	private int getLeavesNum() {
		return data.length << 2;
	}

	private void set(int i) {
		data[i >> 3] |= 1 << (7 ^ (i & 7));
	}

	private void reset(int i) {
		data[i >> 3] &= ~(1 << (7 ^ (i & 7)));
	}

	private byte get(int i) {
		return (byte) ((data[i >> 3] >> (7 ^ (7 & i))) & 1);
	}

	private void update(int p) {
		for (; p > 0; p = ((p - 1) >> 1)) {
			int bro = p + ((p & 1) == 1 ? 1 : -1);
			if (get(p) != 0 && get(bro) != 0)
				set((p - 1) >> 1);
			else
				reset((p - 1) >> 1);
		}
	}

	private int getFree() {
		int i = 0, l = getLeavesNum();
		for (; i < l - 1; i += get(i) != 0 ? 1 : 0)
			i = (i << 1) + 1;
		return i - l + 1;
	}

	private void setUse(int i) {
		int p = i + getLeavesNum() - 1;
		set(p);
		update(p);
	}

	private void resetUse(int i) {
		int p = i + getLeavesNum() - 1;
		reset(p);
		update(p);
	}

	private int block;
	private byte[] data;
	private boolean dirty;
}
