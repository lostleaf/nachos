package nachos.filesys;

import nachos.machine.Disk;
import nachos.machine.Machine;
import nachos.machine.SynchDisk;

public class DiskUtils {
    private static DiskUtils instance = null;

    public static DiskUtils getInstance() {
        if (instance == null)
            instance = new DiskUtils();
        return instance;
    }

    public void readBlock(int blockIdx, int length, byte[] buffer) {
        readBlock(blockIdx, length, buffer, 0);
    }

    public void readBlock(int blockIdx, int length, byte[] buffer, int offset) {
        int secIdx = blockIdx * N_SEC_PER_BLOCK;

        for (int i = 0; i < length; ++i)
            for (int j = 0; j < N_SEC_PER_BLOCK; ++j, ++secIdx, offset += SEC_SIZE)
                disk.readSector(secIdx, buffer, offset);
    }

    public void writeBlock(int blockIdx, int length, byte[] buffer) {
        writeBlock(blockIdx, length, buffer, 0);
    }

    public void writeBlock(int blockIdx, int count, byte[] buffer, int offset) {
        int secIdx = blockIdx * N_SEC_PER_BLOCK;

        for (int i = 0; i < count; ++i)
            for (int j = 0; j < N_SEC_PER_BLOCK; ++j, ++secIdx, offset += SEC_SIZE)
                disk.writeSector(secIdx, buffer, offset);
    }

    private DiskUtils() {
        disk = Machine.synchDisk();
    }

    public static int BLOCK_SIZE = 4 * 1024;
    public static int DISK_SIZE = Disk.SectorSize * Disk.NumSectors;
    public static int SEC_SIZE = Disk.SectorSize;
    public static int N_SEC_PER_BLOCK = BLOCK_SIZE / Disk.SectorSize;

    private SynchDisk disk;
}
