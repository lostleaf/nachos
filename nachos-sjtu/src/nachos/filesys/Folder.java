package nachos.filesys;

import nachos.machine.Lib;

/**
 * Folder is a special type of file used to implement hierarchical filesystem.
 * It maintains a map from filename to the address of the file. There's a
 * special folder called root folder with pre-defined address. It's the origin
 * from where you traverse the entire filesystem.
 * 
 * @author starforever
 */
public class Folder extends INode {

	private Folder() {
		super(TYPE_DIR);
		subDirs = new String[N_MAX_LINK];
		types = new int[N_MAX_LINK];
		links = new int[N_MAX_LINK];
	}

	private Folder(int block, boolean primary, int nValid, int parent, int next) {
		this();
		this.block = block;
		this.primary = primary;
		this.nValid = nValid;
		this.parent = parent;
		this.next = next;
	}

	/** create a new file in the folder and return its address */
	public static Folder create(int block, int parent) {
		Folder dir = new Folder(block, true, 0, parent, -1);
		dir.save();
		return dir;
	}

	/** add an entry with specific filename and address to the folder */
	public boolean addEntry(String subDirName, int type, int block) {
		if (nValid == N_MAX_LINK)
			return false;

		subDirs[nValid] = subDirName;
		types[nValid] = type;
		links[nValid++] = block;

		save();
		return true;
	}

	/** remove an entry from the folder */
	public boolean removeEntry() {
		if (next != -1)
			return false;
		--nValid;
		return true;
	}

	/** load the content of the folder from the disk */
	public static Folder load(int block) {

		byte[] buffer = new byte[DiskUtils.BLOCK_SIZE];
		DiskUtils.getInstance().readBlock(block, 1, buffer);

		Folder dir = new Folder(block, (Lib.bytesToInt(buffer, 4) == 0),
				Lib.bytesToInt(buffer, 8), Lib.bytesToInt(buffer, 12),
				Lib.bytesToInt(buffer, 16));

		int offset = 20, offset1 = offset + 260;

		for (int i = 0; i < dir.nValid; ++i, offset += 268, offset1 += 268) {
			dir.subDirs[i] = new String(buffer, offset + 4, Lib.bytesToInt(
					buffer, offset));
			dir.types[i] = Lib.bytesToInt(buffer, offset1);
			dir.links[i] = Lib.bytesToInt(buffer, 4 + offset1);
		}

		return dir;
	}

	/** save the content of the folder to the disk */
	public void save() {
		byte[] data = getDataHeader();

		int offset = 20, offset1 = 280;
		for (int i = 0; i < nValid; ++i, offset1 += 268, offset += 268) {
			byte[] bytes = subDirs[i].getBytes();

			Lib.bytesFromInt(data, offset, bytes.length);
			System.arraycopy(bytes, 0, data, 4 + offset, bytes.length);
			Lib.bytesFromInt(data, offset1, types[i]);
			Lib.bytesFromInt(data, 4 + offset1, links[i]);
		}

		DiskUtils.getInstance().writeBlock(block, 1, data);
	}

	public FolderEntry find(String fileName) {
		if (fileName.equals("."))
			return new FolderEntry(FolderEntry.FOLDER, block, ".");

		if (fileName.equals(".."))
			return new FolderEntry(FolderEntry.FOLDER, (parent == -1) ? block
					: parent, "..");

		int idx = findSubDirIdx(fileName);
		return (idx == -1) ? null : getEntry(idx);
	}

	public int findSubDirIdx(String name) {
		for (int i = 0; i < nValid; ++i)
			if (subDirs[i].equals(name))
				return i;
		return -1;
	}

	public FolderEntry getEntry(int i) {
		return new FolderEntry(types[i], links[i], subDirs[i]);
	}

	public int getValidCount() {
		return nValid;
	}

	private byte[] getDataHeader() {
		byte[] data = new byte[DiskUtils.BLOCK_SIZE];

		Lib.bytesFromInt(data, 0, getType());
		Lib.bytesFromInt(data, 4, primary ? 1 : 0);
		Lib.bytesFromInt(data, 8, nValid);
		Lib.bytesFromInt(data, 12, parent);
		Lib.bytesFromInt(data, 16, next);
		return data;
	}

	public boolean modifyEntry(int index, String subDirName, int type, int block) {
		if (index >= nValid)
			return false;

		subDirs[index] = subDirName;
		types[index] = type;
		links[index] = block;
		return true;
	}

	public void setNext(int block) {
		next = block;
		save();
	}

	public boolean hasNext() {
		return next != -1;
	}

	public Folder loadNext() {
		return Folder.load(next);
	}

	public int getParent() {
		return parent;
	}

	private int parent;
	private String[] subDirs;
	private int[] types;

	private static final int N_MAX_LINK = 15;
}
