package nachos.filesys;

import nachos.machine.Lib;

public class DirINode extends INode {
    private int parent;
    private String[] subDirs;
    private int[] types;

    private static final int N_MAX_LINK = 15;

    private DirINode() {
        super(TYPE_DIR);
        subDirs = new String[N_MAX_LINK];
        types = new int[N_MAX_LINK];
        links = new int[N_MAX_LINK];
    }

    private DirINode(int block, boolean primary, int nValid, int parent,
            int next) {
        this();
        this.block = block;
        this.primary = primary;
        this.nValid = nValid;
        this.parent = parent;
        this.next = next;
    }

    public static DirINode create(int block, int parent) {
        DirINode dir = new DirINode(block, true, 0, parent, -1);
        dir.save();
        return dir;
    }

    public static DirINode loadFromDisk(int block) {

        byte[] buffer = new byte[DiskUtils.BLOCK_SIZE];
        DiskUtils.getInstance().readBlock(block, 1, buffer);

        DirINode dir = new DirINode(block, (Lib.bytesToInt(buffer, 4) == 0),
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

    public FileEntry find(String fileName) {
        if (fileName.equals("."))
            return new FileEntry(FileEntry.DIRECTORY, block, ".");

        if (fileName.equals(".."))
            return new FileEntry(FileEntry.DIRECTORY, (parent == -1) ? block
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

    public FileEntry getEntry(int i) {
        return new FileEntry(types[i], links[i], subDirs[i]);
    }

    public int getValidCount() {
        return nValid;
    }

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

    private byte[] getDataHeader() {
        byte[] data = new byte[DiskUtils.BLOCK_SIZE];

        Lib.bytesFromInt(data, 0, getType());
        Lib.bytesFromInt(data, 4, primary ? 1 : 0);
        Lib.bytesFromInt(data, 8, nValid);
        Lib.bytesFromInt(data, 12, parent);
        Lib.bytesFromInt(data, 16, next);
        return data;
    }

    public boolean addEntry(String subDirName, int type, int block) {
        if (nValid == N_MAX_LINK)
            return false;

        subDirs[nValid] = subDirName;
        types[nValid] = type;
        links[nValid++] = block;

        save();
        return true;
    }

    public boolean modifyEntry(int index, String subDirName, int type, int block) {
        if (index >= nValid)
            return false;

        subDirs[index] = subDirName;
        types[index] = type;
        links[index] = block;
        return true;
    }

    public boolean removeLastEntry() {
        if (next != -1)
            return false;
        --nValid;
        return true;
    }

    public void setNext(int block) {
        next = block;
        save();
    }

    public boolean hasNext() {
        return next != -1;
    }

    public DirINode loadNext() {
        return DirINode.loadFromDisk(next);
    }

    public int getParent() {
        return parent;
    }
}
