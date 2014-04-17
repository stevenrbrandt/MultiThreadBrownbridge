package gc;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Comparable Lock.
 * @author sbrandt
 *
 */
public class CompLock extends ReentrantLock implements Comparable<CompLock> {

	final int id;
	final Priority priority;

	public CompLock(Priority priority,int id) {
		this.id = id;
		this.priority = priority;
	}

	public int compareTo(CompLock o) {
		if (priority.val < o.priority.val)
			return -1;
		else if (priority.val > o.priority.val)
			return 1;
		else if (id < o.id)
			return -1;
		else if (id > o.id)
			return 1;
		else
			return 0;
	}
	
	@Override
	public int hashCode() {
		return 100*id+priority.val;
	}
	@Override
	public boolean equals(Object o) {
		CompLock cl = (CompLock)o;
		return cl.id == id && cl.priority == priority;
	}

	public String toString() {
		return "Lock[" + priority+"," + id + "]";
	}
}
