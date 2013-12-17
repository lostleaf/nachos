package nachos.filesys;

import nachos.machine.FileSystem;
import nachos.machine.Lib;
import nachos.threads.ThreadedKernel;
import nachos.vm.VMProcess;

/**
 * FilesysProcess is used to handle syscall and exception through some callback
 * methods.
 * 
 * @author starforever
 */
public class FilesysProcess extends VMProcess {

    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
        case SYSCALL_MKDIR:
            return handleMkdir(a0);

        case SYSCALL_RMDIR:
            return handleRmdir(a0);

        case SYSCALL_CHDIR:
            return handleChdir(a0);

        case SYSCALL_GETCWD:
            return handleGetcwd(a0, a1);

        case SYSCALL_READDIR:
            return handleReaddir(a0, a1, a2, a3);

        case SYSCALL_STAT:
            return handleStat(a0, a1);

        case SYSCALL_LINK:
            return handleLink(a0, a1);

        case SYSCALL_SYMLINK:
            return handleSymlink(a0, a1);

        default:
            return super.handleSyscall(syscall, a0, a1, a2, a3);
        }
    }

    @Override
    public String toAbsPath(String s) {
        if (s.startsWith("/"))
            return s;
        else
            return curDir + s;
    }

    @Override
    protected boolean load(String name, String[] args) {
        if (!name.startsWith("/"))
            name = curDir + name;
        return super.load(name, args);
    }

    private int handleMkdir(int pathNameAddr) {
        String dir = readVirtualMemoryString(pathNameAddr, MAX_ARG_LEN);
        if (dir == null)
            return -1;

        return (getFileSystem().createFolder(toAbsPath(dir))) ? 0 : -1;
    }

    private int handleRmdir(int pathNameAddr) {
        String dir = readVirtualMemoryString(pathNameAddr, MAX_ARG_LEN);
        if (dir == null)
            return -1;

        return (getFileSystem().removeFolder(toAbsPath(dir))) ? 0 : -1;
    }

    private int handleChdir(int pathAddr) {
        String dir = readVirtualMemoryString(pathAddr, MAX_ARG_LEN);
        String t = getFileSystem().getFormalPathName(toAbsPath(dir));
        if (dir == null || t == null)
            return -1;

        Lib.debug(dbgFilesys, "change current dir " + curDir);
        curDir = t;

        return 0;
    }

    private int handleGetcwd(int bufferAddr, int size) {
        String s = curDir;

        if (curDir.endsWith("/") && curDir.length() > 1)
            s = curDir.substring(0, curDir.length() - 1);

        byte[] data = s.getBytes();
        byte[] buffer = new byte[data.length + 1];

        System.arraycopy(data, 0, buffer, 0, data.length);
        buffer[data.length] = 0;

        return (buffer.length < size) ? writeVirtualMemory(bufferAddr, data)
                : -1;
    }

    private int handleReaddir(int a0, int a1, int a2, int a3) {
        return -1;
    }

    private int handleStat(int fileNameAddr, int statAddr) {
        String file = readVirtualMemoryString(fileNameAddr, MAX_ARG_LEN);
        if (file == null)
            return -1;

        file = toAbsPath(file);
        FileStat stat = getFileSystem().getStat(file);
        if (stat == null)
            return -1;

        byte[] statNameBytes = stat.name.getBytes();
        if (statNameBytes.length >= 256)
            return -1;

        byte[] buffer = new byte[276];
        System.arraycopy(statNameBytes, 0, buffer, 0, statNameBytes.length);
        buffer[statNameBytes.length] = 0;
        int type = toStatType(stat.type);
        
        final int offset = 256;

        Lib.bytesFromInt(buffer, offset, stat.size);
        Lib.bytesFromInt(buffer, offset + 4, stat.sectors);
        Lib.bytesFromInt(buffer, offset + 8, type);
        Lib.bytesFromInt(buffer, offset + 12, stat.inode);
        Lib.bytesFromInt(buffer, offset + 16, stat.links);

        writeVirtualMemory(statAddr, buffer, 0, buffer.length);

        return 0;
    }

    private int toStatType(int stattype) {
        if (stattype == FolderEntry.FOLDER)
            return 1;
        if (stattype == FolderEntry.PLAIN_FILE)
            return 0;
        if (stattype == FolderEntry.SYMLINK)
            return 2;
        return -1;
    }

    private int handleLink(int oldNameAddr, int newNameAddr) {
        String src = readVirtualMemoryString(oldNameAddr, MAX_ARG_LEN);
        String dst = readVirtualMemoryString(newNameAddr, MAX_ARG_LEN);

        if (src == null || dst == null)
            return -1;

        return (getFileSystem().createLink(toAbsPath(src), toAbsPath(dst))) ? 0
                : -1;
    }

    private int handleSymlink(int oldNameAddr, int newNameAddr) {
        String src = readVirtualMemoryString(oldNameAddr, MAX_ARG_LEN);
        String dst = readVirtualMemoryString(newNameAddr, MAX_ARG_LEN);
        if (src == null || dst == null)
            return -1;

        return (getFileSystem().createSymlink(toAbsPath(src), toAbsPath(dst))) ? 0
                : -1;
    }

    private RealFileSystem getFileSystem() {
        FileSystem sys = ThreadedKernel.fileSystem;
        return (sys instanceof RealFileSystem) ? (RealFileSystem) sys : null;
    }

    protected static final int SYSCALL_MKDIR = 14;
    protected static final int SYSCALL_RMDIR = 15;
    protected static final int SYSCALL_CHDIR = 16;
    protected static final int SYSCALL_GETCWD = 17;
    protected static final int SYSCALL_READDIR = 18;
    protected static final int SYSCALL_STAT = 19;
    protected static final int SYSCALL_LINK = 20;
    protected static final int SYSCALL_SYMLINK = 21;

    protected String curDir = "/";

    private static final char dbgFilesys = 'f';
}
