package nachos.filesys;

import nachos.machine.Machine;
import nachos.machine.Disk;
import nachos.machine.SynchDisk;
import nachos.machine.Disk;

public class DiskHelper {
    private static DiskHelper instance = null;
    
    private SynchDisk disk = null;
    private static int blockSize = (1 << 12); // 4KB
    private static int diskSize = Disk.SectorSize * Disk.NumSectors;
    private static int sectorSize = Disk.SectorSize;
    private static int sectorsPerBlock = blockSize / Disk.SectorSize;

    private DiskHelper() {
        disk = Machine.synchDisk();
    }

    public static DiskHelper getInstance() {
        if (instance == null)
            instance = new DiskHelper();
        return instance;
    }

    public void readBlock(int block, int count, byte[] buffer) {
        readBlock(block, count, buffer, 0);
    }

    public void readBlock(int block, int count, byte[] buffer, int offset) {
        for (int i = 0; i < count; ++i)
            for (int j = 0; j < sectorsPerBlock; ++j)
                disk.readSector((block + i) * sectorsPerBlock + j, buffer, offset + i * blockSize + j * sectorSize);
    }

    public void writeBlock(int block, int count, byte[] buffer) {
        writeBlock(block, count, buffer, 0);
    }

    public void writeBlock(int block, int count, byte[] buffer, int offset) {
        for (int i = 0; i < count; ++i)
            for (int j = 0; j < sectorsPerBlock; ++j)
                disk.writeSector((block + i) * sectorsPerBlock + j, buffer, offset + i * blockSize + j * sectorSize);
    }

    public static int getBlockSize() {
        return blockSize;
    }

    public static long getDiskSize() {
        return diskSize;
    }
}
