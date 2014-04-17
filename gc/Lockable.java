package gc;

/*
 * Lockable objects have an associated
 * Lock which can be obtained through
 * this interface.
 */
public interface Lockable {
	CompLock getLock();
}
