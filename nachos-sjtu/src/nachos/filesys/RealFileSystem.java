package nachos.filesys;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nachos.machine.FileSystem;
import nachos.machine.Machine;
import nachos.machine.OpenFile;

/**
 * RealFileSystem provide necessary methods for filesystem syscall. The
 * FileSystem interface already define two basic methods, you should implement
 * your own to adapt to your task.
 * 
 * @author starforever
 */
public class RealFileSystem implements FileSystem {
	public int getFreeSize() {
		return 0;
	}

	public int getSwapFileSectors() {
		return 0;
	}

	/**
	 * initialize the file system
	 * 
	 * @param format
	 *            whether to format the file system
	 */
	public void init(boolean format) {
		caches = new HashMap<Integer, FileCache>();
		rmFiles = new HashSet<Integer>();

		if (format) {
			createFilesys();
			importStub();
			return;
		}

		SuperBlock superBlock = SuperBlock.load();
		if (superBlock == null)
			createFilesys();
		else
			freeList = FreeList.load(superBlock.freeList);
	}

	public void createFilesys() {
		SuperBlock.create(ROOT_BLOCK, FREE_LIST_BLOCK);
		Folder.create(ROOT_BLOCK, -1);
		freeList = FreeList.create(FREE_LIST_BLOCK, FS_HEAD);
	}

	public void finish() {
		for (FileCache cache : caches.values())
			cache.save();
		for (int file : rmFiles)
			doRemoveFile(file);
		freeList.save();
	}

	public OpenFile open(String name, boolean create) {
		FolderEntry entry = findFileEntry(name, create);
		if (entry == null)
			return null;

		if (entry.type == FolderEntry.PLAIN_FILE) {
			FileCache cache = getFileCache(entry.block);
			cache.incUser();
			return new File(this, cache, name);
		}

		String fileName = getFileName(name);
		if (fileName == null)
			return null;

		Folder folder = Folder.load(entry.block);
		FileINode iNode = createFile(folder, fileName);
		if (iNode == null)
			return null;

		FileCache fileCache = getFileCache(iNode.block);
		fileCache.incUser();
		return new File(this, fileCache, name);
	}

	public boolean remove(String fileName) {
		String path = getFilePath(fileName), name = getFileName(fileName);
		if (path == null || name == null)
			return false;

		FolderEntry folderEntry = findEntry(path, true);
		if (folderEntry == null || folderEntry.type != FolderEntry.FOLDER)
			return false;
		Folder folder = (Folder) folderEntry.load();

		FolderEntry entry = findEntry(folder, name);
		if (entry == null || entry.type == FolderEntry.FOLDER)
			return false;
		removeEntry(folder, name);

		if (entry.type == FolderEntry.PLAIN_FILE) {
			getFileCache(entry.block).getINode().decLinkCount();
			if (!doRemoveFile(entry.block))
				rmFiles.add(new Integer(entry.block));
		} else
			freeList.deallocate(entry.block);

		return true;
	}

	public boolean createFolder(String name) {
		String path = getFilePath(name), dir = getFileName(name);
		if (path == null || dir == null || dir.length() == 0)
			return false;

		FolderEntry entry = findEntry(path, true);
		if (entry == null || entry.type != FolderEntry.FOLDER)
			return false;

		Folder folder = (Folder) entry.load();
		if (findEntry(folder, dir) != null)
			return false;

		int block = freeList.allocate();
		if (block == -1)
			return false;

		Folder.create(block, entry.block);
		if (!addEntry(folder, dir, FolderEntry.FOLDER, block)) {
			freeList.deallocate(block);
			return false;
		}
		return true;
	}

	public boolean removeFolder(String name) {
		String path = getFilePath(name), fileName = getFileName(name);
		if (path == null || fileName == null || fileName.length() == 0)
			return false;

		FolderEntry entry = findEntry(path, true);
		if (entry == null || entry.type != FolderEntry.FOLDER)
			return false;

		Folder folder = (Folder) entry.load();
		FolderEntry t = findEntry(folder, fileName);
		if (t == null || t.type == FolderEntry.PLAIN_FILE)
			return false;

		if (t.type == FolderEntry.SYMLINK) {
			removeEntry(folder, fileName);
			freeList.deallocate(t.block);
		} else {
			Folder dt = (Folder) t.load();
			if (dt.getValidCount() > 0 || dt.hasNext())
				return false;
			removeEntry(folder, fileName);
			freeList.deallocate(t.block);
		}

		return true;
	}

	public String[] readDir(String name) {
		return null;
	}

	public FileStat getStat(String name) {
		FolderEntry entry = findEntry(name, false);
		if (entry == null)
			return null;

		if (entry.type == FolderEntry.SYMLINK)
			return new FileStat(name, 0, FolderEntry.SYMLINK, 0, 0, 0);

		if (entry.type == FolderEntry.FOLDER)
			return new FileStat(name, 0, FolderEntry.FOLDER, 0, 0, 0);

		FileINode iNode = getFileCache(entry.block).getINode();
		return new FileStat(name, iNode.getSize(), FolderEntry.PLAIN_FILE, 0,
				0, iNode.getLinkCount());
	}

	public boolean createLink(String src, String dst) {
		String path = getFilePath(dst), name = getFileName(dst);
		if (path == null || name == null || name.length() == 0)
			return false;

		FolderEntry entrySrc = findEntry(src, true);
		if (entrySrc == null || entrySrc.type != FolderEntry.PLAIN_FILE)
			return false;

		FileCache cache = getFileCache(entrySrc.block);

		FolderEntry entryDst = findEntry(path, true);
		if (entryDst == null || entryDst.type != FolderEntry.FOLDER)
			return false;

		Folder folder = (Folder) entryDst.load();
		if (findEntry(folder, name) != null)
			return false;

		cache.setDirty();
		cache.getINode().incLinkCount();
		if (addEntry(folder, name, FolderEntry.PLAIN_FILE, entrySrc.block) == false) {
			cache.getINode().decLinkCount();
			return false;
		}

		return true;
	}

	public boolean createSymlink(String src, String dst) {
		String path = getFilePath(dst), name = getFileName(dst);
		if (path == null || name == null || name.length() == 0)
			return false;

		FolderEntry entryDst = findEntry(path, true);
		if (entryDst == null || entryDst.type != FolderEntry.FOLDER)
			return false;

		Folder folder = (Folder) entryDst.load();
		if (findEntry(folder, name) != null)
			return false;

		int block = freeList.allocate();
		SymINode.create(block, src);

		if (!addEntry(folder, name, FolderEntry.SYMLINK, block)) {
			freeList.deallocate(block);
			return false;
		}
		return true;
	}

	public String getFormalPathName(String name) {
		FolderEntry entry = findEntry(name, true);
		if (entry == null || entry.type != FolderEntry.FOLDER)
			return null;
		return makePathName(entry.block, -1);
	}

	private String getNameOrPath(String fileName, boolean isPath) {
		if (!fileName.startsWith("/"))
			return null;
		if (fileName.endsWith("/"))
			fileName = fileName.substring(0, fileName.length() - 1);
		int pos = fileName.lastIndexOf("/");
		return isPath ? fileName.substring(0, pos + 1) : fileName
				.substring(pos + 1);
	}

	private String getFilePath(String fileName) {
		return getNameOrPath(fileName, true);
	}

	private String getFileName(String fileName) {
		return getNameOrPath(fileName, false);
	}

	private FolderEntry findEntry(Folder folder, String fileName) {
		FolderEntry entry = folder.find(fileName);
		if (entry != null)
			return entry;
		return (folder.hasNext()) ? findEntry(folder.loadNext(), fileName)
				: null;
	}

	private String findEntryName(Folder parent, int block) {
		for (int i = 0; i < parent.getValidCount(); ++i) {
			FolderEntry entry = parent.getEntry(i);
			if (entry.block == block)
				return entry.name;
		}
		return (parent.hasNext()) ? findEntryName(parent.loadNext(), block)
				: null;
	}

	private FolderEntry findEntry(String fileName, boolean isReal, int depth) {
		if (depth < 0 || !fileName.startsWith("/"))
			return null;

		if (fileName.endsWith("/"))
			fileName = fileName.substring(0, fileName.length() - 1);

		int pos = fileName.indexOf("/"), nextPos;

		Folder cur = Folder.load(ROOT_BLOCK);
		if (pos == -1)
			return new FolderEntry(FolderEntry.FOLDER, ROOT_BLOCK, "/");

		for (;; pos = nextPos) {
			nextPos = fileName.indexOf("/", pos + 1);

			String entryName;
			if (nextPos != -1)
				entryName = fileName.substring(pos + 1, nextPos);
			else
				entryName = fileName.substring(pos + 1);

			FolderEntry entry = findEntry(cur, entryName);
			if (entry == null)
				return null;

			if (entry.type == FolderEntry.SYMLINK) {
				SymINode sym = (SymINode) entry.load();

				String str;
				if (nextPos != -1) {
					str = sym.getTarget()
							+ (sym.getTarget().endsWith("/") ? "" : "/");
					str += fileName.substring(nextPos + 1);
				} else {
					if (!isReal)
						return entry;
					str = sym.getTarget();
				}
				return findEntry(str, isReal, depth - 1);
			} else {
				if (nextPos == -1)
					return entry;
				if (entry.type != FolderEntry.FOLDER)
					return null;
				cur = (Folder) entry.load();
			}
		}
	}

	private FolderEntry findEntry(String fileName, boolean isSym) {
		return findEntry(fileName, isSym, 128);
	}

	private FolderEntry findFileEntry(String fileName, boolean isParent,
			int depth) {
		if (depth < 0 || fileName.endsWith("/"))
			return null;

		String path = getFilePath(fileName), name = getFileName(fileName);
		if (path == null || name == null)
			return null;

		FolderEntry folderEntry = findEntry(path, true);
		if (folderEntry == null || folderEntry.type != FolderEntry.FOLDER)
			return null;

		Folder folder = (Folder) folderEntry.load();
		FolderEntry entry = findEntry(folder, name);

		if (entry != null) {
			if (entry.type == FolderEntry.PLAIN_FILE)
				return entry;

			if (entry.type == FolderEntry.SYMLINK)
				return findFileEntry(((SymINode) entry.load()).getTarget(),
						false, depth - 1);

			return null;
		}
		return isParent ? folderEntry : null;
	}

	private FolderEntry findFileEntry(String fileName, boolean parent) {
		return findFileEntry(fileName, parent, 128);
	}

	/** import from stub filesystem */
	private void importStub() {
		FileSystem stubFS = Machine.stubFileSystem();
		FileSystem realFS = this;
		String[] file_list = Machine.stubFileList();
		for (int i = 0; i < file_list.length; ++i) {
			if (!file_list[i].endsWith(".coff"))
				continue;
			OpenFile src = stubFS.open(file_list[i], false);
			if (src == null) {
				continue;
			}
			OpenFile dst = realFS.open("/" + file_list[i], true);
			int size = src.length();
			byte[] buffer = new byte[size];
			src.read(0, buffer, 0, size);
			dst.write(0, buffer, 0, size);
			src.close();
			dst.close();
		}
	}

	private FileCache getFileCache(int block) {
		if (!caches.containsKey(new Integer(block))) {
			FileINode iNode = FileINode.load(block);
			FileCache cache = new FileCache(iNode);
			caches.put(new Integer(block), cache);
		}
		return caches.get(new Integer(block));
	}

	private FileINode createFile(Folder folder, String fileName) {
		int pos = freeList.allocate();
		if (pos == -1)
			return null;
		FileINode iNode = FileINode.create(pos, true, 1);
		if (!addEntry(folder, fileName, FolderEntry.PLAIN_FILE, pos)) {
			freeList.deallocate(pos);
			return null;
		}
		return iNode;
	}

	public boolean doRemoveFile(int block) {
		FileCache cache = getFileCache(block);
		if (cache.hasUser())
			return false;
		if (cache.getINode().getLinkCount() == 0)
			freeFile(cache.getINode());
		if (rmFiles.contains(block))
			rmFiles.remove(block);
		return true;
	}

	private String makePathName(int block, int lower) {
		Folder dir = null;
		String name = "";
		if (block != ROOT_BLOCK || lower != -1)
			dir = Folder.load(block);
		if (lower != -1)
			name = findEntryName(dir, lower) + "/";
		if (block == ROOT_BLOCK)
			return "/" + name;
		return makePathName(dir.getParent(), block) + name;
	}

	private boolean addEntry(Folder folder, String name, int type, int block) {
		Folder current = folder;
		while (!current.addEntry(name, type, block)) {
			if (current.hasNext())
				current = current.loadNext();
			else {
				int dirPos = freeList.allocate();
				if (dirPos == -1)
					return false;
				Folder next = Folder.create(dirPos, current.getParent());
				current.setNext(dirPos);
				current = next;
			}
		}
		return true;
	}

	private boolean removeEntry(Folder folder, String name) {
		Folder cur = folder, last = null, target = null;
		int pos = -1;
		while (true) {
			if (pos == -1) {
				pos = cur.findSubDirIdx(name);
				if (pos != -1)
					target = cur;
			}
			if (!cur.hasNext())
				break;
			last = cur;
			cur = cur.loadNext();
		}

		if (cur.getValidCount() == 0)
			return false;
		FolderEntry subs = cur.getEntry(cur.getValidCount() - 1);
		if (target == null)
			return false;
		target.modifyEntry(pos, subs.name, subs.type, subs.block);
		cur.removeEntry();

		if (cur.getValidCount() == 0 && last != null) {
			last.setNext(-1);
			freeList.deallocate(cur.getBlock());
		} else {
			target.save();
			if (target.getBlock() != cur.getBlock())
				cur.save();
		}
		return true;
	}

	private void freeFile(FileINode iNode) {
		int block = iNode.getBlock();
		caches.remove(new Integer(block));
		for (FileINode cur = iNode; cur != null; cur = cur.hasNext() ? cur
				.loadNext() : null) {
			for (int i = 0; i < cur.getValidCount(); ++i)
				freeList.deallocate(cur.getLink(i));
			int curBlock = cur.getBlock();
			freeList.deallocate(curBlock);
		}
		caches.remove(block);
	}

	public FreeList freeList;
	private Map<Integer, FileCache> caches;
	public Set<Integer> rmFiles;
	private static final int ROOT_BLOCK = 1, FREE_LIST_BLOCK = 2, FS_HEAD = 2;
}
