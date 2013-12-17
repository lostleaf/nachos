package nachos.filesys;

public class FileCache {

	public FileCache(FileINode iNode) {
		this.iNode = iNode;
		this.dirty = false;
		this.nUser = 0;
	}

	public void incUser() {
		++nUser;
	}

	public void decUser() {
		--nUser;
	}

	public boolean hasUser() {
		return nUser > 0;
	}

	public void setDirty() {
		dirty = true;
	}

	public FileINode getINode() {
		return iNode;
	}

	public void save() {
		if (!dirty)
			return;

		iNode.save();
		dirty = false;
	}

	private boolean dirty;
	private int nUser;
	private FileINode iNode;

}
