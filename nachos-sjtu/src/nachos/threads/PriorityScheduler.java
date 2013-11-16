package nachos.threads;

import java.util.HashSet;
import java.util.TreeSet;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

/**
 * A scheduler that chooses threads based on their priorities.
 * 
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 * 
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 * 
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 * 
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer priority from
	 *            waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum) {
			Machine.interrupt().restore(intStatus); // bug identified by Xiao
													// Jia @ 2011-11-04
			return false;
		}

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum) {
			Machine.interrupt().restore(intStatus); // bug identified by Xiao
													// Jia @ 2011-11-04
			return false;
		}

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread
	 *            the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	protected int getUpdatePriority(int pri, PriorityQueue p) {
		return Math.max(pri, p.pickNextThread().effPriority);
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	public class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// TODO

			ThreadState state = pickNextThread();
			if (state != null) {
				waitSet.remove(state);
				state.waitingPQ = null;
				state.acquire(this);
				return state.thread;
			}
			return null;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 * 
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			if (!waitSet.isEmpty())
				return waitSet.last();
			return null;
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			for (ThreadState state : waitSet)
				System.out
						.println(state.thread + ": " + state.getPriority()
								+ " " + state.getEffectivePriority() + " "
								+ state.time);
		}

		public boolean isEmpty() {
			return waitSet.isEmpty();
		}

		public void remove(ThreadState e) {
			// System.out.println(this.toString() + "before remove:"
			// + e.thread.toString());
			// print();
			waitSet.remove(e);
			// System.out.println("after remove");
			// print();
		}

		public boolean add(ThreadState e) {
			// System.out.println(this.toString() + "before add:"
			// + e.thread.toString());
			// print();
			boolean ret = waitSet.add(e);
			// System.out.println("after add:");
			// print();
			return ret;
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		TreeSet<ThreadState> waitSet = new TreeSet<ThreadState>();
		public ThreadState waitingTS;

	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	public class ThreadState implements Comparable<ThreadState> {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 * 
		 * @param thread
		 *            the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			waitedSet = new HashSet<PriorityQueue>();
			setPriority(priorityDefault);
			effPriority = priorityDefault;
			time = 0;
		}

		/**
		 * Return the priority of the associated thread.
		 * 
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 * 
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			return effPriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 * 
		 * @param priority
		 *            the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			this.priority = priority;

			changePriority(this);
		}

		private void changePriority(ThreadState state) {
			// System.out.println(state.thread);
			for (; state != null; state = state.waitingPQ.waitingTS) {
				int pri = state.priority;
				for (PriorityQueue p : state.waitedSet)
					if (p.transferPriority && !p.isEmpty())
						pri = getUpdatePriority(pri, p);
				// pri = Math.max(pri, p.pickNextThread().effPriority);

				if (pri == state.effPriority)
					return;

				if (state.waitingPQ == null) {
					state.effPriority = pri;
					return;
				}

				// change the priority
				state.waitingPQ.remove(state);
				state.effPriority = pri;
				state.waitingPQ.add(state);

				if (!state.waitingPQ.transferPriority)
					return;
			}
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 * 
		 * @param waitQueue
		 *            the queue that the associated thread is now waiting on.
		 * 
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			if (waitingPQ != null)
				waitingPQ.remove(this);
			waitingPQ = waitQueue;

			// System.out.println("before remove" + this.thread.toString());
			// waitQueue.print();

			waitQueue.remove(this);

			// System.out.println("after remove" + this.thread.toString());
			// waitQueue.print();

			time = Machine.timer().getTime();

			// System.out.println("before add" + this.thread.toString());
			// waitQueue.print();

			waitQueue.add(this);

			// System.out.println("after add" + this.thread.toString());
			// waitQueue.print();

			if (waitQueue.transferPriority) {
				ThreadState owner = waitQueue.waitingTS;
				if (owner != null && owner.effPriority < this.effPriority)
					changePriority(owner);
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 * 
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			if (waitQueue.waitingTS != null) {
				waitQueue.waitingTS.waitedSet.remove(waitQueue);
				if (waitQueue.transferPriority)
					changePriority(waitQueue.waitingTS);
			}
			waitQueue.waitingTS = this;

			waitedSet.add(waitQueue);
			if (waitQueue.transferPriority)
				changePriority(this);
		}

		@Override
		public int compareTo(ThreadState o) {
			int ep = getEffectivePriority(), epo = o.getEffectivePriority();
			return (ep != epo) ? ep - epo : (o.time != time ? sgn(o.time, time)
					: thread.compareTo(o.thread));
		}

		// in case of overflowing
		private int sgn(long a, long b) {
			return a < b ? -1 : 1;
		}

		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority, effPriority;
		private long time; // Time of Reaching
		public PriorityQueue waitingPQ = null;// the waiting priority queue
		public HashSet<PriorityQueue> waitedSet;
	}
}
