package gc;

/**
 * Lock priorities. @see Lockable
 * @author sbrandt
 *
 */
public enum Priority {
	GLOBAL(1), SOBJECT(3), COLLECTOR(2), LIST(0), COLLECTCOUNTER(-1);
	final int val;
	Priority(int val) { this.val = val; }
}
