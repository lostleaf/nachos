package nachos.filesys;

import nachos.machine.Lib;

public class FileEntry {

    public FileEntry(int type, int block) {
        this(type, block, null);
    }

    public FileEntry(int type, int block, String name) {
        this.type = type;
        this.block = block;
        this.name = name;
    }

    public INode load() {
        if (type == DIRECTORY)
            return DirINode.loadFromDisk(block);
        if (type == PLAIN_FILE)
            return PlainINode.loadFromDisk(block);
        if (type == SYMLINK)
            return SymINode.loadFromDisk(block);

        Lib.assertNotReached("Invalid entry type");
        return null;
    }

    public int type;
    public int block;
    public String name;

    public final static int DIRECTORY = 0;
    public final static int PLAIN_FILE = 1;
    public final static int SYMLINK = 2;
}
