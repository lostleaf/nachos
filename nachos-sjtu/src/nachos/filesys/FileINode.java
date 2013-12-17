package nachos.filesys;

import nachos.machine.Lib;

public class FileINode extends INode {
	private int size;
	private int linkCount;

	private static final int N_MAX_LINK = 1000;

	private FileINode() {
		super(TYPE_FILE);
		links = new int[N_MAX_LINK];
	}

	private FileINode(int block, boolean primary, int nValid, int size,
			int linkCount, int next) {
		this();
		this.block = block;
		this.primary = primary;
		this.nValid = nValid;
		this.size = size;
		this.linkCount = linkCount;
		this.next = next;
	}

	public static FileINode create(int block, boolean primary, int linkCount) {
		FileINode file = new FileINode(block, primary, 0, 0, linkCount, -1);
		file.save();
		return file;
	}

	public static FileINode load(int block) {
		byte[] buffer = new byte[DiskUtils.BLOCK_SIZE];
		DiskUtils.getInstance().readBlock(block, 1, buffer);

		FileINode file = new FileINode(block, Lib.bytesToInt(buffer, 4) == 0,
				Lib.bytesToInt(buffer, 8), Lib.bytesToInt(buffer, 12),
				Lib.bytesToInt(buffer, 16), Lib.bytesToInt(buffer, 20));

		for (int i = 0; i < file.nValid; ++i)
			file.links[i] = Lib.bytesToInt(buffer, 24 + i * 4);

		return file;
	}

	public void read(int i, byte[] buffer, int offset) {
		DiskUtils.getInstance().readBlock(links[i], 1, buffer, offset);
	}

	public void write(int i, byte[] buffer, int offset) {
		DiskUtils.getInstance().writeBlock(links[i], 1, buffer, offset);
	}

	public void save() {
		byte[] data = new byte[DiskUtils.BLOCK_SIZE];

		Lib.bytesFromInt(data, 0, getType());
		Lib.bytesFromInt(data, 4, primary ? 1 : 0);
		Lib.bytesFromInt(data, 8, nValid);
		Lib.bytesFromInt(data, 12, size);
		Lib.bytesFromInt(data, 16, linkCount);
		Lib.bytesFromInt(data, 20, next);

		for (int i = 0; i < nValid; ++i)
			Lib.bytesFromInt(data, 24 + i * 4, links[i]);

		DiskUtils.getInstance().writeBlock(block, 1, data);
	}

	public FileINode loadNext() {
		return FileINode.load(next);
	}

	public int getSize() {
		return size;
	}

	public void setSize(int newSize) {
		size = newSize;
	}

	public int getValidCount() {
		return nValid;
	}

	public int getLink(int i) {
		return links[i];
	}

	public boolean addLink(int block) {
		if (nValid >= N_MAX_LINK)
			return false;

		links[nValid++] = block;
		return true;
	}

	public void setNext(int next) {
		this.next = next;
	}

	public void incLinkCount() {
		++linkCount;
	}

	public void decLinkCount() {
		--linkCount;
	}

	public int getLinkCount() {
		return linkCount;
	}

	public boolean hasNext() {
		return next != -1;
	}
}
