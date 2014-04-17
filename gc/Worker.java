package gc;

import java.util.LinkedList;

public class Worker extends Thread {
	public static Worker[] WORKERS = new Worker[8];
	static {
		for(int i=0;i<WORKERS.length;i++)
			WORKERS[i] = new Worker();
	}
	
	public static LinkedList<Xthread> work = new LinkedList<Xthread>();
	
	public static synchronized Xthread get() {
		if(work.isEmpty())
			return null;
		return work.removeFirst();
	}
	
	public Worker() {
		start();
	}
	
	public void run() {
		try {
			while(true) {
				Runnable run = get();
				if(run != null)
					run.run();
			}
		} catch(Throwable t) {
			t.printStackTrace();
		}
		System.exit(0);
	}

	public static synchronized void add(Xthread run) {
		work.addLast(run);
	}
}
