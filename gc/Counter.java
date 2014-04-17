package gc;

public class Counter {
	int refCount;
	int storeCount;
	
	WrappedLock lockCounter = new WrappedLock(Priority.COLLECTCOUNTER);

	public void incRef() {
		try {
			Locker.start(lockCounter);
			refCount++;
		} finally {
			Locker.end();
		}
	}
	
	public void decRef() {
		try {
			Locker.start(lockCounter);
			refCount--;
			if(refCount == 0)
				lockCounter.cond.signalAll();
			assert refCount >= 0 : this;
		} finally {
			Locker.end();
		}
	}
	
	public void incStore() {
		try {
			Locker.start(lockCounter);
			storeCount++;
			if(storeCount == 1)
				lockCounter.cond.signalAll();
		} finally {
			Locker.end();
		}
	}
	public void decStore() {
		try {
			Locker.start(lockCounter);
			storeCount--;
			assert storeCount >= 0 : this;
		} finally {
			Locker.end();
		}
	}
	
	public String toString() {
		return "COUNT: store="+storeCount+"/ref="+refCount;
	}
	
	public boolean action() {
		try {
			Locker.start(lockCounter);
			while(true) {
				if(storeCount > 0)
					return true;
				if(refCount == 0)
					return false;
				assert Here.here(this);
				try {
					lockCounter.cond.await();
				} catch (InterruptedException e) {
				}
			}
		} finally {
			Locker.end();
		}
	}

	public int getRefCount() {
		try {
			Locker.start(lockCounter);
			return refCount;
		} finally {
			Locker.end();
		}
	}

	public boolean continueWaiting() {
		try {
			Locker.start(lockCounter);
			return refCount > 0 && storeCount == 0;
		} finally {
			Locker.end();
		}
	}

	public boolean done() {
		try {
			Locker.start(lockCounter);
			return refCount == 0 && storeCount == 0;
		} finally {
			Locker.end();
		}
	}
}
