package gc;

import java.util.concurrent.locks.Condition;

public class WrappedLock extends IdObject implements Lockable {
	
	final CompLock cmplock;
	final Condition cond;
	
	public WrappedLock(Priority p) {
		cmplock = new CompLock(p,id);
		cond = cmplock.newCondition();
	}

	public CompLock getLock() {
		return cmplock;
	}

}
