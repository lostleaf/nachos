package nachos.filesys;

public abstract class INode {

    public INode(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public int getBlock() {
        return block;
    }

    public abstract void save();

    protected int block, type, nValid, next;
    protected boolean primary;
    protected int[] links;

    final public static int TYPE_DIR = 1;
    final public static int TYPE_FILE = 2;
    final public static int TYPE_SYM = 3;
}
