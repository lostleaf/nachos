package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.io.EOFException;
import java.util.*;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        pageTable = new TranslationEntry[Machine.processor().getNumPhysPages()];
        for (int i = 0; i < pageTable.length; ++i)
            pageTable[i] = new TranslationEntry(i, 0, false, false, false,
                    false);
        numPages = 0;

        processLock.acquire();
        pid = ++pidCounter;
        pidProcMap.put(pid, this);
        processLock.release();

        openFiles.put(0, UserKernel.console.openForReading());
        openFiles.put(1, UserKernel.console.openForWriting());
        fileId = 1024;
    }

    /**
     * Allocate and return a new process of the correct class. The class name is
     * specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     * 
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     * 
     * @param name
     *            the name of the file containing the executable.
     * @param args
     *            the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(toAbsPath(name), args))
            return false;

        ++aliveProcNum;

        setParent(UserKernel.currentProcess());
        thread = (UThread) (new UThread(this).setName(name));
        thread.fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    protected boolean allocPages(int vpn, int desiredPages, boolean readOnly) {
        LinkedList<TranslationEntry> allocated = new LinkedList<TranslationEntry>();

        for (int i = 0; i < desiredPages; ++i) {
            if (vpn >= pageTable.length)
                return false;

            int ppn = UserKernel.allocPage();
            if (ppn == -1) {
                Lib.debug(dbgProcess, "\tcannot allocate new page");

                for (TranslationEntry te : allocated) {
                    pageTable[te.vpn] = new TranslationEntry(te.vpn, 0, false,
                            false, false, false);
                    UserKernel.freePage(te.ppn);
                    --numPages;
                }

                return false;
            } else {
                TranslationEntry a = new TranslationEntry(vpn + i, ppn, true,
                        readOnly, false, false);
                allocated.add(a);
                pageTable[vpn + i] = a;
                ++numPages;
            }
        }
        return true;
    }

    protected void releaseResource() {
        for (int i = 0; i < pageTable.length; ++i)
            if (pageTable[i].valid) {
                UserKernel.freePage(pageTable[i].ppn);
                pageTable[i] = new TranslationEntry(pageTable[i].vpn, 0, false,
                        false, false, false);
            }
        numPages = 0;
    }

    protected TranslationEntry translate(int vaddr) {
        return findPageTable(UserKernel.vpn(vaddr));
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read at
     * most <tt>maxLength + 1</tt> bytes from the specified address, search for
     * the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     * 
     * @param vaddr
     *            the starting virtual address of the null-terminated string.
     * @param maxLength
     *            the maximum number of characters in the string, not including
     *            the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     *         found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     * 
     * @param vaddr
     *            the first byte of virtual memory to read.
     * @param data
     *            the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    protected TranslationEntry findPageTable(int vpn) {
        if (pageTable == null)
            return null;
        return (vpn >= 0 && vpn < pageTable.length) ? pageTable[vpn] : null;
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no data
     * could be copied).
     * 
     * @param vaddr
     *            the first byte of virtual memory to read.
     * @param data
     *            the array where the data will be stored.
     * @param offset
     *            the first byte to write in the array.
     * @param length
     *            the number of bytes to transfer from virtual memory to the
     *            array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0
                && offset + length <= data.length);

        TranslationEntry te = translate(vaddr);
        if (te == null || !te.valid)
            return 0;

        int addrOffset = UserKernel.offset(vaddr);
        int paddr = UserKernel.addr(te.ppn, addrOffset);

        byte[] memory = Machine.processor().getMemory();

        int amount = Math.min(length, pageSize - addrOffset);
        System.arraycopy(memory, paddr, data, offset, amount);

        if (amount < length)
            return amount
                    + readVirtualMemory(UserKernel.addr(te.vpn + 1, 0), data,
                            offset + amount, length - amount);
        else
            return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     * 
     * @param vaddr
     *            the first byte of virtual memory to write.
     * @param data
     *            the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no data
     * could be copied).
     * 
     * @param vaddr
     *            the first byte of virtual memory to write.
     * @param data
     *            the array containing the data to transfer.
     * @param offset
     *            the first byte to transfer from the array.
     * @param length
     *            the number of bytes to transfer from the array to virtual
     *            memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0
                && offset + length <= data.length);

        TranslationEntry te = translate(vaddr);
        if (te == null || !te.valid || te.readOnly)
            return 0;

        int addrOffset = UserKernel.offset(vaddr);
        int paddr = UserKernel.addr(te.ppn, addrOffset);

        byte[] memory = Machine.processor().getMemory();

        int amount = Math.min(length, pageSize - addrOffset);
        System.arraycopy(data, offset, memory, paddr, amount);

        if (amount < length)
            return amount
                    + writeVirtualMemory(UserKernel.addr(te.vpn + 1, 0), data,
                            offset + amount, length - amount);
        else
            return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     * 
     * @param name
     *            the name of the file containing the executable.
     * @param args
     *            the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    protected boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            if (!allocPages(numPages, section.getLength(), section.isReadOnly())) {
                releaseResource();
                return false;
            }
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        if (!allocPages(numPages, stackPages, false)) {
            releaseResource();
            return false;
        }
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        if (!allocPages(numPages, 1, false)) {
            releaseResource();
            return false;
        }

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be run
     * (this is the last step in process initialization that can fail).
     * 
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            return false;
        }

        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                TranslationEntry entry = findPageTable(vpn);
                if (entry == null)
                    return false;
                section.loadPage(i, entry.ppn);
            }
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of the
     * stack, set the A0 and A1 registers to argc and argv, respectively, and
     * initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < Processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    protected int handleHalt() {

        if (pid == 1)
            finishWith(0);

        return 0;
    }

    protected int createOrOpen(int a0, boolean create) {
        String fileName = readVirtualMemoryString(a0, MAX_ARG_LEN);
        if (fileName == null)
            return -1;

        OpenFile of = ThreadedKernel.fileSystem.open(toAbsPath(fileName), create);
        if (of == null)
            return -1;

        int id = getNextFileId();
        openFiles.put(id, of);
        return id;
    }

    protected int handleCreat(int nameAddr) {
        return createOrOpen(nameAddr, true);
    }

    protected int handleOpen(int nameAddr) {
        return createOrOpen(nameAddr, false);
    }

    protected int handleRead(int fd, int bufferAddr, int size) {
        OpenFile of = openFiles.get(fd);
        if (of == null)
            return -1;

        byte[] buffer = new byte[size];
        int ret = of.read(buffer, 0, size);
        if (ret == -1 || writeVirtualMemory(bufferAddr, buffer, 0, ret) != ret)
            return -1;
        return ret;
    }

    protected int handleWrite(int fd, int bufferAddr, int size) {
        OpenFile of = openFiles.get(fd);
        if (of == null)
            return -1;

        byte[] buffer = new byte[size];
        if (readVirtualMemory(bufferAddr, buffer, 0, size) != size)
            return -1;

        processLock.acquire();
        int ret = of.write(buffer, 0, size);
        processLock.release();

        return ret;
    }

    protected int handleClose(int fd) {
        if (!openFiles.containsKey(new Integer(fd)))
            return -1;

        openFiles.remove(fd).close();
        return 0;
    }

    protected int handleUnlink(int nameAddr) {
        String file = readVirtualMemoryString(nameAddr, MAX_ARG_LEN);
        if (file == null)
            return -1;

        if (ThreadedKernel.fileSystem.remove(toAbsPath(file)))
            return 0;
        else
            return -1;
    }

    public String toAbsPath(String s) {
        return s;
    }

    protected int handleExec(int nameAddr, int argc, int argvAddr) {
        String file = readVirtualMemoryString(nameAddr, MAX_ARG_LEN);
        if (file == null)
            return -1;

        if (argc < 0)
            return -1;

        String[] args = new String[argc];
        for (int i = 0; i < argc; ++i) {
            byte[] buffer = new byte[4];
            if (readVirtualMemory(argvAddr + i * 4, buffer) != buffer.length)
                return -1;
            int addr = Lib.bytesToInt(buffer, 0);
            args[i] = readVirtualMemoryString(addr, MAX_ARG_LEN);
            if (args[i] == null)
                return -1;
        }

        UserProcess child = UserProcess.newUserProcess();
        if (!child.execute(file, args))
            return -1;

        return child.getPid();
    }

    protected int handleJoin(int pid, int statusAddr) {
        if (!pidProcMap.containsKey(new Integer(pid)))
            return -1;

        UserProcess child = pidProcMap.get(new Integer(pid));
        if (child.getParent() != this)
            return -1;

        child.thread.join();
        child.setParent(null);

        if (child.status != 0)
            return 0;

        writeVirtualMemory(statusAddr, Lib.bytesFromInt(child.code));
        return 1;
    }

    protected int handleExit(int status) {
        code = status;
        finishWith(0);
        return 0;
    }

    protected void finishWith(int cause) {
        status = cause;
        coff.close();
        for (OpenFile of : openFiles.values())
            of.close();

        for (UserProcess p : pidProcMap.values())
            if (p.getParent() == this)
                p.setParent(null);

        releaseResource();

        boolean shouldHalt = false;

        processLock.acquire();
        --aliveProcNum;
        shouldHalt = (aliveProcNum == 0);
        processLock.release();

        if (shouldHalt)
            Kernel.kernel.terminate();
        else
            UThread.finish();
    }

    protected static final int syscallHalt = 0, syscallExit = 1,
            syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
            syscallOpen = 5, syscallRead = 6, syscallWrite = 7,
            syscallClose = 8, syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     * 
     * <table>
     * <tr>
     * <td>syscall#</td>
     * <td>syscall prototype</td>
     * </tr>
     * <tr>
     * <td>0</td>
     * <td><tt>void halt();</tt></td>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td><tt>void exit(int status);</tt></td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td><tt>int  exec(char *name, int argc, char **argv);
     *                              </tt></td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td><tt>int  join(int pid, int *status);</tt></td>
     * </tr>
     * <tr>
     * <td>4</td>
     * <td><tt>int  creat(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>5</td>
     * <td><tt>int  open(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>6</td>
     * <td><tt>int  read(int fd, char *buffer, int size);
     *                              </tt></td>
     * </tr>
     * <tr>
     * <td>7</td>
     * <td><tt>int  write(int fd, char *buffer, int size);
     *                              </tt></td>
     * </tr>
     * <tr>
     * <td>8</td>
     * <td><tt>int  close(int fd);</tt></td>
     * </tr>
     * <tr>
     * <td>9</td>
     * <td><tt>int  unlink(char *name);</tt></td>
     * </tr>
     * </table>
     * 
     * @param syscall
     *            the syscall number.
     * @param a0
     *            the first syscall argument.
     * @param a1
     *            the second syscall argument.
     * @param a2
     *            the third syscall argument.
     * @param a3
     *            the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
        case syscallHalt:
            return handleHalt();

        case syscallCreate:
            return handleCreat(a0);

        case syscallOpen:
            return handleOpen(a0);

        case syscallRead:
            return handleRead(a0, a1, a2);

        case syscallWrite:
            return handleWrite(a0, a1, a2);

        case syscallClose:
            return handleClose(a0);

        case syscallUnlink:
            return handleUnlink(a0);

        case syscallExec:
            return handleExec(a0, a1, a2);

        case syscallJoin:
            return handleJoin(a0, a1);

        case syscallExit:
            return handleExit(a0);

        default:
            finishWith(UserProcess.exceptionIllegalSyscall);
            Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
     * . The <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     * 
     * @param cause
     *            the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
        case Processor.exceptionSyscall:
            int result = handleSyscall(processor.readRegister(Processor.regV0),
                    processor.readRegister(Processor.regA0),
                    processor.readRegister(Processor.regA1),
                    processor.readRegister(Processor.regA2),
                    processor.readRegister(Processor.regA3));
            processor.writeRegister(Processor.regV0, result);
            processor.advancePC();
            break;

        default:
            Lib.debug(dbgProcess, "Unexpected exception: "
                    + Processor.exceptionNames[cause]);
            finishWith(cause);
            Lib.assertNotReached("Unexpected exception");
        }
    }

    // getters and setters
    protected void setParent(UserProcess p) {
        parent = p;
    }

    protected UserProcess getParent() {
        return parent;
    }

    public int getPid() {
        return pid;
    }

    public String getSwapFileName() {
        return "SWAP";
    }

    protected int getNextFileId() {
        return ++fileId;
    }

    public static final int exceptionIllegalSyscall = 100;

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = Config.getInteger(
            "Processor.numStackPages", 8);

    protected int initialPC, initialSP;
    protected int argc, argv;

    protected static final int pageSize = Processor.pageSize;
    protected static final char dbgProcess = 'a';

    protected Map<Integer, OpenFile> openFiles = new HashMap<Integer, OpenFile>();
    protected int fileId;
    protected static final int MAX_ARG_LEN = 256;

    protected static int pidCounter = 0;
    protected int pid;

    protected static Map<Integer, UserProcess> pidProcMap = new HashMap<Integer, UserProcess>();
    protected static Lock processLock = new Lock();
    protected static int aliveProcNum = 0;

    protected int status = 0, code = 0;

    protected UThread thread = null;
    protected UserProcess parent = null;
}
