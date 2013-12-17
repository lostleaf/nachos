package nachos.filesys;

import java.util.LinkedList;

import nachos.machine.OpenFile;

/**
 * File provide some basic IO operations. Each File is associated with an INode
 * which stores the basic information for the file.
 * 
 * @author starforever
 */
public class File extends OpenFile {

	public File(RealFileSystem realFS, FileCache cache, String fileName) {
		super(realFS, fileName);
		this.cache = cache;
		this.realFS = realFS;
		this.freeList = realFS.freeList;
		this.open = true;
		this.pos = 0;
	}

	public int length() {
		return cache.getINode().getSize();
	}

	public void close() {
		open = false;
		cache.decUser();
		if (cache.hasUser())
			return;

		int block = cache.getINode().getBlock();
		if (realFS.rmFiles.contains(new Integer(block)))
			realFS.doRemoveFile(block);
	}

	public void seek(int pos) {
		this.pos = pos;
	}

	public int tell() {
		return pos;
	}

	public int read(byte[] buffer, int start, int limit) {
		int len = read(pos, buffer, start, limit);
		pos += (len != -1) ? len : 0;
		return len;
	}

	public int write(byte[] buffer, int start, int limit) {
		int len = write(pos, buffer, start, limit);
		pos += (len != -1) ? len : 0;
		return len;
	}

	public int read(int pos, byte[] buffer, int start, int limit) {
		if (!open || pos < 0 || limit < 0)
			return -1;

		if (limit == 0 || length() == 0)
			return 0;
		byte[] data = new byte[DiskUtils.BLOCK_SIZE];
		int firstBlock = pos / DiskUtils.BLOCK_SIZE;

		int readLen = 0;
		for (int i = firstBlock; readLen < limit; ++i) {
			int begin = (i == firstBlock) ? (pos % DiskUtils.BLOCK_SIZE) : 0, end = readBlock(
					i, data, 0);
			int amount = Math.min(end - begin, limit - readLen);
			if (amount <= 0)
				break;

			System.arraycopy(data, begin, buffer, start + readLen, amount);
			readLen += amount;

			if (end < DiskUtils.BLOCK_SIZE)
				break;
		}
		return readLen;
	}

	public int write(int pos, byte[] buffer, int start, int limit) {

		if (!open || pos < 0 || limit < 0)
			return -1;
		if (limit == 0)
			return 0;
		if (length() < pos + limit && !expand(pos + limit))
			return -1;

		int firstBlock = pos / DiskUtils.BLOCK_SIZE;
		byte[] tmp = new byte[DiskUtils.BLOCK_SIZE];

		int writeLen = 0;
		for (int i = firstBlock; writeLen < limit; ++i) {
			int begin = (i == firstBlock) ? (pos % DiskUtils.BLOCK_SIZE) : 0;
			int amount = Math.min(DiskUtils.BLOCK_SIZE - begin, limit
					- writeLen);

			if (amount < DiskUtils.BLOCK_SIZE)
				readBlock(i, tmp, 0);
			System.arraycopy(buffer, start + writeLen, tmp, begin, amount);
			writeBlock(i, tmp, 0);
			writeLen += amount;
		}

		return writeLen;
	}

	private int getBlockCount() {
		return (length() == 0) ? 0
				: ((length() - 1) / DiskUtils.BLOCK_SIZE) + 1;
	}

	private int readBlock(int block, byte[] buffer, int offset) {
		if (block >= getBlockCount())
			return 0;

		FileINode iNode = cache.getINode();
		for (; block >= iNode.getValidCount(); iNode = iNode.loadNext())
			block -= iNode.getValidCount();

		iNode.read(block, buffer, offset);

		int position = 0;
		if (block == getBlockCount() - 1)
			position = length() % DiskUtils.BLOCK_SIZE;

		return (position == 0) ? DiskUtils.BLOCK_SIZE : position;
	}

	private int writeBlock(int block, byte[] buffer, int offset) {
		if (block >= getBlockCount())
			return 0;

		FileINode iNode = cache.getINode();
		for (; block >= iNode.getValidCount(); iNode = iNode.loadNext())
			block -= iNode.getValidCount();

		iNode.write(block, buffer, offset);

		int position = 0;
		if (block == getBlockCount() - 1)
			position = length() % DiskUtils.BLOCK_SIZE;

		return (position == 0) ? DiskUtils.BLOCK_SIZE : position;
	}

	private boolean expand(int newSize) {

		cache.setDirty();

		FileINode iNode = cache.getINode();
		int capacity = getBlockCount() * DiskUtils.BLOCK_SIZE;

		if (capacity >= newSize) {
			iNode.setSize(newSize);
			return true;
		}

		int nBlockNeed = (newSize - capacity - 1) / DiskUtils.BLOCK_SIZE + 1;
		LinkedList<Integer> blocks = new LinkedList<Integer>();

		for (int i = 0; i < nBlockNeed; ++i) {
			int t = freeList.allocate();
			if (t == -1) {
				for (Integer p : blocks)
					freeList.deallocate(p.intValue());
				return false;
			}
			blocks.add(new Integer(t));
		}

		FileINode cur = iNode;
		while (!blocks.isEmpty()) {
			int block = blocks.remove();

			if (cur.addLink(block) == false) {
				int alloc = freeList.allocate();
				if (alloc == -1)
					return false;

				FileINode next = FileINode.create(alloc, false, 0);
				cur.setNext(alloc);

				if (cur != iNode)
					cur.save();
				cur = next;

				cur.addLink(block);
			}
		}

		iNode.setSize(newSize);
		return true;
	}

	private FileCache cache;
	private boolean open;
	private int pos;
	private FreeList freeList;
	private RealFileSystem realFS;
}
