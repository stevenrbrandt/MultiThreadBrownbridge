package gc;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public class Main {
	public static void main(String[] args) {
		try {
			simplecycle();

			simplecycle2();

			wheel();

			multiCollect();

			doublyLinkedList();

			clique();

			benzeneRingScalability(6);

			benzeneRingMStest();

			sobjectSetTest();

			int x = 1;
			while(x<=16){
				long st = System.currentTimeMillis();
				benzeneRingScalability(x);
				status();
				long endTime = System.currentTimeMillis();
				long totalTime = endTime-st;
				Here.here("Time for "+x+" is "+totalTime);
				x=x+1;
			}		status();
		} catch(Throwable e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

	private static void sobjectSetTest() {
		int n = 2;

		Root a[][] = new Root[n][6];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < 6; j++) {
				a[i][j] = new Root();
				a[i][j].alloc();
			}

		}

		// create benzene
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < 6; j++) {
				a[i][j].set("next",
						a[i][(j + 1) % 6].get());
			}
		}


		// partially free
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < 5; j++) {
				a[i][j].free();
			}
		}

		assert Here.here("Benzene finished");

		int raceChecks = 0;
		for(int i = 0; i < n; i++){
			boolean phantomized = false; 
			try {
				Locker.start(a[i][5].get(),a[i][5].get("next"));
				if(a[i][5].get("next").phantomized==true) {
					a[i][5].set(a[i][5].get("next"));
					raceChecks++;
					phantomized = true;
				}


			}finally{
				Locker.end();
			}
			if(!phantomized) {
				int ncount = 0;
				while(!a[i][5].get("next").phantomized){
					// This loop is too busy,
					// can cause hanging
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
					}
					// Give up if it takes more than 10 tries
					// The GC didn't fail, the test just didn't
					// create the scenerio we were hoping to see.
					if(ncount++ == 10) break;
				}
				a[i][5].set(a[i][5].get("next"));
				raceChecks++;
			}
		}
		if(raceChecks==n){
			for(int i = 1; i < n ; i++){
				a[i][5].set("next1", a[i-1][5].get());
			}
			for(int i = 0; i < n; i++){
				a[i][5].free();
			}
		}
	}

	private static void benzeneRingMStest() {
		int n = 8;

		Root a[][] = new Root[n][6];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < 6; j++) {
				a[i][j] = new Root();
				a[i][j].alloc();
			}

		}

		// create benzene
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < 6; j++) {
				a[i][j].set(String.valueOf((j + 1) % 8),
						a[i][(j + 1) % 6].get());
			}
		}


		for (int j = n - 1; j > 0; j--) {
			a[j][4].set("next2", a[j - 1][2].get());
		}

		for (int i = 0; i < n; i++) {
			for (int j = 1; j < 6; j++) {
				a[i][j].free();
			}
		}
		assert Here.here("Benzene finished");

		for (int i = 1; i < n; i++) {
			a[i][0].free();
		}
	}

	private static void benzeneRingScalability(int size) {
		int n = size*16;
		Root a[][] = new Root[n][6];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < 6; j++) {
				a[i][j] = new Root();
				a[i][j].alloc();
			}

		}

		// create benzene
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < 6; j++) {
				a[i][j].set(String.valueOf((j + 1) % 8),
						a[i][(j + 1) % 6].get());
			}
		}

		for (int i = 0; i < n - 1; i++) {
			a[i][1].set("next1", a[i + 1][5].get());
		}
		for (int j = n - 1; j > 0; j--) {
			a[j][4].set("next2", a[j - 1][2].get());
		}

		for (int i = 0; i < n; i++) {
			for (int j = 1; j < 6; j++) {
				a[i][j].free();
			}
		}
		assert Here.here("Benzene finished");
		Xthread.waitForZeroThreads();
		long start = System.currentTimeMillis();

		for (int i = 0; i < n; i++) {
			a[i][0].free();
		}
		Xthread.waitForZeroThreads();
		long end = System.currentTimeMillis();
		System.out.println("Time="+0.001*(end-start));
	}

	public static void status() {
		Xthread.waitForZeroThreads();
		Sobject.status();

		markAndSweep();
	}

	/**
	 * This method is used to verify that our
	 * garbage collector obtained a correct result.
	 */
	private static void markAndSweep() {
		assert Here.here("Mark and Sweep");

		ArrayDeque<Sobject> pqueue = new ArrayDeque<Sobject>();

		if(pqueue == null || Root.roots==null)
			assert Here.here("null queue");
		else 
			assert Here.here("size "+Root.roots.size());
		if(Root.roots.size()>0)
			pqueue.addAll(Root.roots);
		while(!pqueue.isEmpty()){
			Sobject so = pqueue.poll();
			if(so.mark==false){
				so.mark=true;
				for(Entry<String, Link> l : so.links.entrySet()){
					Link li = l.getValue();
					pqueue.add(li.target);
				}
			}
		}

		Set<Sobject> copy = new HashSet<Sobject>();
		copy.addAll(Sobject.objects);
		int live = 0;
		Sobject.Sobjectlive = Sobject.live();
		for (Sobject so : copy) {
			if(so.mark==true){
				assert so.count[so.which.value]>0 : so;
				assert so.count[so.which.flip().value]>=0;
				assert so.count[2]==0;
				assert so.deleted==false;
				assert so.collector == null;
				live++;
				so.mark=false;
			}
			else{
				assert so.deleted==true : so.toString();
			}

		}
		assert Here.here("After Mark and Sweep Live = "+live);
		assert Here.here("live = "+live+" slive = "+Sobject.Sobjectlive);
		if(live == Sobject.Sobjectlive){
			Here.here(" MS == B");
		}
		else {
			Here.here( " MS != B "+live+" == "+Sobject.Sobjectlive);
			throw new Error();
		}
	}

	private static void clique() {
		int n = 10;
		Root a[] = new Root[n];
		for (int i = 0; i < n; i++) {
			a[i] = new Root();
			a[i].alloc();
		}
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i != j)
					a[i].set("x" + String.valueOf(i) + String.valueOf(j),
							a[j].get());
			}
		}

		for (int i = n - 1; i >= 0; i--) {
			a[i].free();
		}
	}

	private static void doublyLinkedList() {
		Root dl = dlink(null, 30);
		// Move to other end of linked list
		while (true) {
			Sobject so = dl.get().get("prev");
			if (so == null)
				break;
			dl.set(so);
		}
		dl.free();
	}

	private static void multiCollect() {
		Root aone = new Root();
		aone.alloc();
		Root prev = new Root();
		prev.set(aone.get());
		final int n = 1;
		for (int i = 0; i <= n; i++) {
			Root x = new Root();
			x.alloc();
			prev.set("next", x.get());
			prev.set(x.get());
			x.free();
		}
		assert Here.here("First One Done");
		Root atwo = new Root();
		atwo.alloc();
		Root prev2 = new Root();
		prev2.set(atwo.get());
		final int nt = 1;
		for (int i = 0; i <= nt; i++) {
			Root x = new Root();
			x.alloc();
			prev2.set("next", x.get());
			prev2.set(x.get());
			x.free();
		}

		assert Here.here("Second One DOne");
		prev.set("n", atwo.get());
		prev2.set("m", aone.get());
		assert Here.here("two chain joined");
		prev.free();
		prev2.free();
		aone.free();
		atwo.free();
	}

	private static void wheel() {
		Root wheel = new Root();
		wheel.alloc();
		Root prev = new Root();
		prev.set(wheel.get());
		final int n = 500;
		for (int i = 0; i <= n; i++) {
			Root x = new Root();
			x.alloc();
			prev.set("next", x.get());
			prev.set(x.get());
			if (i == n) {
				x.set("next", wheel.get());
			}
			x.free();
		}
		prev.free();
		assert Here.here("Finished creating cycle");
		for (int i = 0; i < 300; i++) {
			// Here.here("i="+i);
			Locker.start(wheel.get());
			Sobject so = wheel.get("next");
			Locker.end();
			Locker.start(wheel.get(), so);
			// Here.here("SO is "+so);
			assert so != null;
			wheel.set(so);
			Locker.end();
		}
		Xthread.waitForZeroThreads();
		Main.markAndSweep();
		for (int i = 0; i < 300; i++) {
			// Here.here("i="+i);
			Locker.start(wheel.get());
			Sobject so = wheel.get("next");
			Locker.end();
			Locker.start(wheel.get(), so);
			// Here.here("SO is "+so);
			assert so != null;
			wheel.set(so);
			Locker.end();
		}
		assert Here.here();
		wheel.free();
	}

	private static void simplecycle() {
		Root a = new Root();
		a.alloc();
		Root b = new Root();
		b.alloc();

		a.set("x", b.get());
		b.set("x", a.get());
		a.set("y", a.get());
		assert Here.here("a=" + a.get());
		assert Here.here("b=" + b.get());
		Sobject pa = a.get();
		Sobject pb = b.get();

		b.free();

		assert Here.here("a=" + pa);
		assert Here.here("b=" + pb);
		a.free();

		assert Here.here("a=" + pa);
		assert Here.here("b=" + pb);
	}
	
	private static void simplecycle2() {
		Root a = new Root();
		a.alloc();
		Root b = new Root();
		b.alloc();

		a.set("x", b.get());
		b.set("x", a.get());
//		a.set("y", a.get());
		assert Here.here("a=" + a.get());
		assert Here.here("b=" + b.get());
		Sobject pa = a.get();
		Sobject pb = b.get();

		b.free();

		assert Here.here("a=" + pa);
		assert Here.here("b=" + pb);
		a.set("y",null);
//		b.set("x",null);
		try {
			Thread.sleep(3);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		a.free();

		assert Here.here("a=" + pa);
		assert Here.here("b=" + pb);
	}

	private static Root dlink(Sobject o, int n) {
		Root a = new Root();
		a.alloc();
		Root ret = null;
		if (o != null) {
			o.set("next", a.get());
			a.get().set("prev", o);
			if (n > 0) {
				ret = dlink(a.get(), n - 1);
				a.free();
			} else {
				return a;
			}
		} else {
			ret = dlink(a.get(), n - 1);
			a.free();
		}
		return ret;
	}
}
