package nachos.threads;

import nachos.machine.Lib;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

/**
 * A scheduler that chooses threads using a lottery.
 * 
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * 
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * 
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new lottery thread queue.
	 * 
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer tickets from
	 *            waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	public static final int priorityMaximum = Integer.MAX_VALUE;
	public static final int priorityMinimum = 1;

	@Override
	protected int getUpdatePriority(int pri, PriorityQueue p) {
		for (ThreadState ts : p.waitSet)
			pri += ts.effPriority;
		return pri;
	}

	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}

	public class LotteryQueue extends PriorityQueue {

		@Override
		protected ThreadState pickNextThread() {
			int t = 0;
			for (ThreadState ts : waitSet)
				t += ts.effPriority;
			int choice = Lib.random(t + 1);
			for (ThreadState ts : waitSet) {
				t += ts.effPriority;
				if (t + 1 >= choice)
					return ts;
			}
			return null;
		}

		LotteryQueue(boolean transferPriority) {
			super(transferPriority);
		}

	}
}
