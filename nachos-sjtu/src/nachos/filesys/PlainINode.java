package nachos.filesys;

import nachos.machine.Lib;

public class PlainINode extends INode {
    private int size;
    private int linkCount;

    private static final int maxLinkCount = 1000;

    private PlainINode() {
        super(TYPE_FILE);
        links = new int[maxLinkCount];
    }

    public static PlainINode loadFromDisk(int block) {
        PlainINode nf = new PlainINode();
        nf.block = block;

        byte[] buffer = new byte[DiskUtils.BLOCK_SIZE];
        DiskUtils.getInstance().readBlock(block, 1, buffer);

        Lib.assertTrue(nf.getType() == Lib.bytesToInt(buffer, 0), "Normal file inode type doesn't match");

        if (Lib.bytesToInt(buffer, 4) == 0)
            nf.primary = false;
        else
            nf.primary = true;

        nf.nValid = Lib.bytesToInt(buffer, 8);
        Lib.assertTrue(nf.nValid >= 0 && nf.nValid <= maxLinkCount, "Normal file valid count is out of range");

        nf.size = Lib.bytesToInt(buffer, 12);
        
        nf.linkCount = Lib.bytesToInt(buffer, 16);

        nf.next = Lib.bytesToInt(buffer, 20);

        for (int i = 0; i < nf.nValid; ++i)
            nf.links[i] = Lib.bytesToInt(buffer, 24 + i * 4);

        return nf;
    }

    public static PlainINode create(int block, boolean primary, int linkCount) {
        PlainINode nf = new PlainINode();
        nf.block = block;

        nf.primary = primary;
        nf.nValid = 0;
        nf.size = 0;
        nf.linkCount = linkCount;
        nf.next = -1;

        nf.save();
        return nf;
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

    public int getSize() {
        return size;
    }

    public void setSize(int newSize) {
        size = newSize;
    }

    public int getValidCount() {
        return nValid;
    }

    public PlainINode loadNext() {
        Lib.assertTrue(next != -1);

        return PlainINode.loadFromDisk(next);
    }

    public int getLink(int i) {
        return links[i];
    }

    public void read(int i, byte[] buffer, int offset) {
        Lib.assertTrue(i < nValid);

        DiskUtils.getInstance().readBlock(links[i], 1, buffer, offset);
    }

    public void write(int i, byte[] buffer, int offset) {
        Lib.assertTrue(i < nValid);

        DiskUtils.getInstance().writeBlock(links[i], 1, buffer, offset);
    }

    public boolean addLink(int block) {
        if (nValid < maxLinkCount) {
            links[nValid++] = block;
            return true;
        } else
            return false;
    }

    public void setNext(int next) {
        this.next = next;
    }

    public void increaseLinkCount() {
        ++linkCount;
    }

    public void decreaseLinkCount() {
        Lib.assertTrue(linkCount > 0);
        --linkCount;
    }

    public int getLinkCount() {
        return linkCount;
    }

    public boolean hasNext() {
        return next != -1;
    }
}
