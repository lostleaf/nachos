package nachos.filesys;

import nachos.machine.Lib;

/**
 * FolderEntry contains information used by Folder to map from filename to
 * address of the file
 * 
 * @author starforever
 * */
public class FolderEntry {

	public FolderEntry(int type, int block) {
		this(type, block, null);
	}

	public FolderEntry(int type, int block, String name) {
		this.type = type;
		this.block = block;
		this.name = name;
	}

	public INode load() {
		if (type == FOLDER)
			return Folder.load(block);
		if (type == PLAIN_FILE)
			return FileINode.load(block);
		if (type == SYMLINK)
			return SymINode.loadFromDisk(block);

		Lib.assertNotReached("Invalid entry type");
		return null;
	}

	public int type;
	public int block;
	/** the file name */

	public String name;

	public final static int FOLDER = 0;
	public final static int PLAIN_FILE = 1;
	public final static int SYMLINK = 2;
}
