package nachos.threads;

import java.util.LinkedList;
import java.util.List;

import nachos.ag.BoatGrader;

public class Boat {
	static BoatGrader bg;

	static private Lock lock;
	static private Condition cv;
	static private int nAdultsOahu, nChildrenOahu, nChildrenMolokai;
	static private int boatLoc;
	private static boolean missingOne;
	private static final int OAHU = 0, MOLOKAI = 1;

	public static void selfTest() {
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		// System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		// begin(1, 2, b);

		// System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		// begin(3, 3, b);
	}

	static void forkThreads(String s, int n, List<KThread> threads, Runnable r) {
		for (int i = 0; i < n; i++) {
			KThread kt = new KThread(r);
			kt.setName(s + i);
			kt.fork();
			threads.add(kt);
		}
	}

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		lock = new Lock();
		cv = new Condition(lock);
		nAdultsOahu = adults;
		nChildrenOahu = children;
		nChildrenMolokai = 0;
		boatLoc = OAHU;
		missingOne = false;

		List<KThread> threads = new LinkedList<KThread>();

		forkThreads("Adult", adults, threads, new Runnable() {
			@Override
			public void run() {
				AdultItinerary();
			}
		});

		forkThreads("Child", children, threads, new Runnable() {
			@Override
			public void run() {
				ChildItinerary();
			}
		});

		// make sure judging thread is waiting
		for (KThread kt : threads)
			kt.join();
	}

	static void AdultItinerary() {
		/*
		 * This is where you should put your solutions. Make calls to the
		 * BoatGrader to show that it is synchronized. For example:
		 * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
		 * across to Molokai
		 */

		lock.acquire();

		// Adults are only awake at Oahu since they never come back
		while (boatLoc == MOLOKAI || nChildrenMolokai == 0)
			cv.sleep();

		nAdultsOahu--;
		boatLoc = MOLOKAI;
		bg.AdultRowToMolokai();
		cv.wakeAll();

		lock.release();
	}

	static void ChildItinerary() {
		int loc = OAHU;
		lock.acquire();
		while (nAdultsOahu + nChildrenOahu > 0) {
			if (boatLoc == loc) {
				switch (loc) {
				case MOLOKAI:
					nChildrenMolokai--;
					nChildrenOahu++;

					boatLoc = OAHU;
					loc = OAHU;

					bg.ChildRowToOahu();

					cv.wakeAll();
					break;
				case OAHU:
					if (missingOne) {
						missingOne = false;

						loc = MOLOKAI;
						boatLoc = MOLOKAI;

						nChildrenOahu -= 2;
						nChildrenMolokai += 2;

						bg.ChildRideToMolokai();
						cv.wakeAll();
					} else if (nChildrenOahu > 1) {
						missingOne = true;
						loc = MOLOKAI;
						bg.ChildRowToMolokai();
					} else if(nAdultsOahu==0){
						nChildrenMolokai--;
						nChildrenOahu++;

						boatLoc = MOLOKAI;
						loc = MOLOKAI;
						
						bg.ChildRowToMolokai();

					} else
						cv.sleep();
					break;
				}
			} else
				cv.sleep();
		}
		lock.release();
	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out
				.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}
