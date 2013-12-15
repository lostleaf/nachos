package nachos.filesys;

import nachos.machine.Lib;

public class SymINode extends INode {
    private String target;

    private SymINode(int block, String target) {
        super(TYPE_SYM);
        this.block = block;
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    public static SymINode create(int block, String target) {
        SymINode sym = new SymINode(block, target);
        sym.save();
        return sym;
    }

    public static SymINode loadFromDisk(int block) {
        byte[] buffer = new byte[DiskUtils.BLOCK_SIZE];
        DiskUtils.getInstance().readBlock(block, 1, buffer);

        int len = Lib.bytesToInt(buffer, 4);
        String target = new String(buffer, 8, len);

        return new SymINode(block, target);
    }

    public void save() {
        byte[] data = new byte[DiskUtils.BLOCK_SIZE];
        byte[] targetBytes = target.getBytes();

        Lib.bytesFromInt(data, 0, getType());
        Lib.bytesFromInt(data, 4, targetBytes.length);

        System.arraycopy(targetBytes, 0, data, 8, targetBytes.length);

        DiskUtils.getInstance().writeBlock(block, 1, data);
    }
}
