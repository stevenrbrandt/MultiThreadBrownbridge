package gc;

import java.util.LinkedList;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The locker manages locks in a way that keeps the code safe
 * from deadlocks and other bad behavior. A thread can only obtain
 * more locks if (1) it doesn't have any yet, or (2) the new locks
 * have lower "priority" than any locks already obtained.
 * 
 * @author sbrandt
 *
 */
public class Locker {
	TreeMap<CompLock, Integer> locks = new TreeMap<CompLock,Integer>();
	static final ThreadLocal<Locker> currentLocks = new ThreadLocal<Locker>();
	LinkedList<TreeSet<CompLock>> stack = new LinkedList<TreeSet<CompLock>>();

	/**
	 * Sort locks and lock them.
	 * @param locks
	 */
	public static void start(Lockable... locks) {
		Locker lk = currentLocks.get();
		if (lk == null) {
			lk = new Locker();
			currentLocks.set(lk);
		}
		// Determine the maximum priority of existing locks
		// All new locks must have lower priority than maxPriority.
		int maxPriority = Integer.MAX_VALUE;
		for (CompLock lock : lk.locks.keySet()) {
			if(lock.priority.val < maxPriority)
				maxPriority = lock.priority.val;
		}
		// Sort the current set of locks
		TreeSet<CompLock> tlocks = new TreeSet<CompLock>();
		for (Lockable lock : locks) {
			if (lock != null && lock.getLock() != null)
				tlocks.add(lock.getLock());
		}
		TreeSet<CompLock> newLocks = new TreeSet<CompLock>();
		lk.stack.addLast(newLocks);
		for (CompLock lock : tlocks) {
			if (lk.locks.containsKey(lock)) {
				int count = lk.locks.get(lock);
				lk.locks.put(lock, count+1);
				newLocks.add(lock);
			} else if (!lk.locks.containsKey(lock) && lock.priority.val < maxPriority) {
				lk.locks.put(lock, 1);
				lock.lock();
				newLocks.add(lock);
			} else {
				int num = 0;
				for(CompLock ll : lk.locks.keySet()) {
					System.err.println("NUM="+num);
					num++;
				}
				assert false : "Possible Deadlock " + lock + " " + lk.locks;
				System.exit(0);
			}
		}
	}

	/**
	 * Unlock the set of locks obtained by the last
	 * call to start().
	 */
	public static void end() {
		Locker lk = currentLocks.get();
		TreeSet<CompLock> locks = lk.stack.removeLast();
		for (CompLock lock : locks) {
			int count = lk.locks.get(lock);
			if (count == 1) {
				lk.locks.remove(lock);
				lock.unlock();
			} else {
				lk.locks.put(lock, count - 1);
			}
		}
	}
}

