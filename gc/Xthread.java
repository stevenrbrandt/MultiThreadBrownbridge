package gc;

public class Xthread extends Thread {

	final private BRunnable run;
	
	private volatile static int count = 0;
	
	final static WrappedLock lock = new WrappedLock(Priority.LIST);

	public Xthread() {
		run = null;
	}

	public Xthread(BRunnable run) {
		this.run = run;
	}

	public boolean xrun() {
		return run.run();
	}
	
	@Override
	public void start() {

		try {
			Locker.start(lock);
			count++;
		} finally {
			Locker.end();
		}
		
		Worker.add(this);
	}

	public static int getThreadCount() {
		try {
			Locker.start(lock);
			return count;
		} finally {
			Locker.end();
		}
	}
	
	public static boolean waitForZeroThreads() {
		try {
			Locker.start(lock);
			if(count > 0) {
				try {
					lock.cond.await();
				} catch (InterruptedException e) {
				}
			}
			return count == 0;
		} finally {
			Locker.end();
		}
	}

	public final void run() {
		
		boolean again = false;
		try {
			again = xrun();
		} catch(Throwable t) {
			t.printStackTrace(System.err);
			System.err.flush();
			System.exit(0);
		}
		
		if(again) {
			Worker.add(this);
		} else {
			try {
				Locker.start(lock);
				count--;
				if(count == 0) {
					lock.cond.signalAll();
				}
			} finally {
				Locker.end();
			}
		}
	}
}
