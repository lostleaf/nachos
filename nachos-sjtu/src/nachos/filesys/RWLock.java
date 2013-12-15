package nachos.filesys;

import nachos.threads.Lock;
import nachos.threads.Condition;

public class RWLock {

    public RWLock() {
        lock = new Lock();
        cond = new Condition(lock);
    }

    public void acquireRead() {
        lock.acquire();

        while (writeCount > 0)
            cond.sleep();
        ++readCount;

        lock.release();
    }

    public void acquireWrite() {
        lock.acquire();

        while (writeCount > 0 || readCount > 0)
            cond.sleep();
        ++writeCount;

        lock.release();
    }

    public void releaseRead() {
        lock.acquire();

        if (--readCount == 0)
            cond.wakeAll();

        lock.release();
    }

    public void releaseWrite() {
        lock.acquire();

        --writeCount;
        cond.wakeAll();

        lock.release();
    }

    private Lock lock;
    private Condition cond;
    private int readCount = 0, writeCount = 0;
}
