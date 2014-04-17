package gc;

import java.util.concurrent.locks.Condition;

public class Collector extends IdObject implements BRunnable, Lockable {
	final CompLock lock;
	final Condition cond;

	volatile boolean terminated = false;

	public void checkTerminated() {
		try {
			Locker.start(this);
			assert !terminated;
		} finally {
			Locker.end();
		}
	}

	private void terminate() {
		try {
			Locker.start(this,forward);
			terminated = true;
			setForward(null);
			assert count.done();
		} finally {
			Locker.end();
		}
	}

	Xthread th;

	volatile Collector forward = null;
	
	public void setForward(Collector f) {
		try {
			Locker.start(this,forward,f);
			if(f == forward)
				return;
			if(f != null)
				f.count.incRef();
			if(forward != null)
				forward.count.decRef();
			if(f != null) {
				assert forward == null; 
			}
			forward = f;
		} finally {
			Locker.end();
		}
	}

	final Counter count = new Counter();
	final public SafeList<Sobject> collect = new SafeList<Sobject>(count);
	final public SafeList<Sobject> mergedList = new SafeList<Sobject>(count);
	final public SafeList<Sobject> recoveryList = new SafeList<Sobject>(count);
	final public SafeList<Sobject> rebuildList = new SafeList<Sobject>(count);
	final public SafeList<Sobject> cleanList = new SafeList<Sobject>(count);

	public Collector() {
		assert Here.here();
		lock = new CompLock(Priority.COLLECTOR,id);
		cond = lock.newCondition();
		assert Here.here();
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		try {
			Locker.start(this);
			int fid = forward == null ? -1 : forward.id;
			sb.append("id:" + this.id);
			sb.append(": collector size " + collect.size());
			sb.append(":rec size" + mergedList.size());
			sb.append("fwd ptr:" + fid);
		} finally {
			Locker.end();
		}
		return sb.toString();
	}

	// Pseudo: AddToCollector
	public void add(Sobject so) {
		Collector addTo = this;
		while(true) {
			try {
				Locker.start(addTo, so);
				assert !addTo.terminated : count;
				// fix collector pointers in objects
				// on the fly
				if(addTo.forward != null) {
					addTo = addTo.forward;
					continue;
				}
//				Here.here();
				so.count[so.which.value]++;
				so.count[2]++; // to this here rather than phantomizeNode
				// the reason is because a phantom count of one prevents
				// the collector from being set to null
				addTo.collect.add(so);
				so.setCollector(addTo); // what if it has a collector already?
				break;
			} finally {
				Locker.end();
			}
		}
	}

	public static void requestDelete(final Sobject so) {
		so.where = new Throwable();
//	assert so.where==null;
		new Xthread() {
			@Override
			public boolean xrun() {
				so.die();
				return false;
			}
		}.start();
	}

	public boolean run() {
		if(count.continueWaiting())
			return true;
		try {
			try {
				Locker.start(this);

				while(true) {
					Sobject so = mergedList.poll();
					if(so == null)
						break;
					recoveryList.add(so);
				}

			} finally {
				Locker.end();
			}

			// Perform the collection
			while (true) {
				Sobject so = collect.poll();
				if(so == null)
					break;
				so.phantomizeNode(this);
				recoveryList.add(so);
			}

			while(true) {
				Sobject so = recoveryList.poll();
				if(so == null)
					break;
				so.recoverNode(rebuildList,this); // don't need to pass rebuild
				cleanList.add(so);
			}
			
			while(true) {
				Sobject so = rebuildList.poll();
				if(so == null)
					break;
				so.recoverNode(rebuildList,this);
			}

			while(true) {
				Sobject so = cleanList.poll();
				if(so == null)
					break;
				so.cleanNode(this);
			} 
			
			assert Here.here("done one cycle "+count+
					" m="+mergedList.size()+
					",rc="+recoveryList.size()+
					",cl="+cleanList.size()+
					",co="+collect.size()+
					",r="+rebuildList.size());
			try {
				Locker.start(this,forward);
				boolean done = count.done();
				if(done) {
					terminate();
					return false;
				}
			} finally {
				Locker.end();
			}
		} catch(Throwable t) {
			// Have to catch and log the exception here,
			// otherwise it might get swallowed by an
			// assertion error in the finally block
			t.printStackTrace(System.err);
			System.exit(0);
		} finally {
			assert Here.here("Terminated" + this);
		}
		return true;
	}

	public CompLock getLock() {
		return lock;
	}

	public void merge(Collector s) {
		try {
			Locker.start(this,s);
			assert Here.here("S=" + s);
			assert Here.here("T=" + this);
			
			s.forwardSafeListsTo(this);

			//s.forward = this;
			s.setForward(this);

		} finally {
			Locker.end();
		}

	}
	
	/**
	 * The only safe way to compare whether
	 * two collectors are equal.
	 * @param c2
	 * @return
	 */
	public boolean equals(Collector c2) {
		Collector c1 = this;
		while(true) {
			try {
				Locker.start(c1,c2);
				if(c1 == c2) {
					return true;
				} else if(c1.forward != null) {
					c1 = c1.forward;
				} else if(c2.forward != null) {
					c2 = c2.forward;
				} else {
					return c1 == c2;
				}
			} finally {
				Locker.end();
			}
		}
	}

	public Collector update() {
		Collector c = this;
		while(true) {
			try {
				Locker.start(c);
				if(c.forward == null)
					return c;
				else
					c = c.forward;
			} finally {
				Locker.end();
			}
		}
	}

	private void forwardSafeListsTo(Collector s) {
		try {
			// s is forwarding
			// this is receiving
			Locker.start(this,s);
			collect.forward(s.collect);
			mergedList.forward(s.mergedList);
			
			// Don't send to the merged list, that's only for links
			// the collector holds a phantom count to
			rebuildList.forward(s.rebuildList);
			
			cleanList.forward(s.mergedList);
			recoveryList.forward(s.mergedList);
		} finally {
			Locker.end();
		}
	}
}
