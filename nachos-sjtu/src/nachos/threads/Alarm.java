package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.Machine;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		waitpq = new PriorityQueue<WaitKThread>();
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		KThread.yield();

		boolean intStatus = Machine.interrupt().disable();

		while (!waitpq.isEmpty()
				&& waitpq.peek().time <= Machine.timer().getTime())
			waitpq.poll().kThread.ready();

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x
	 *            the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		boolean intStatus = Machine.interrupt().disable();
		
		waitpq.add(new WaitKThread(Machine.timer().getTime() + x, KThread
				.currentThread()));
		KThread.sleep();
		
		Machine.interrupt().restore(intStatus);
	}

	class WaitKThread implements Comparable<WaitKThread> {
		public long time;
		public KThread kThread;

		public WaitKThread(long time, KThread kThread) {
			this.time = time;
			this.kThread = kThread;
		}

		@Override
		public int compareTo(WaitKThread o) {
			return time < o.time ? -1 : (time == o.time ? 0 : 1);
		}
	}

	PriorityQueue<WaitKThread> waitpq;
}
