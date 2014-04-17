package gc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import gc.Here;

public class Sobject extends IdObject implements Lockable, Cloneable {
	CompLock lock = new CompLock(Priority.SOBJECT,id);
	volatile Map<String, Link> links = new HashMap<String, Link>();
	Bit which = Bit.ZERO;
	volatile boolean phantomized = false;
	boolean deleted = false;
	volatile long weight;
	volatile long maxweight;
	volatile int[] count = { 0, 0, 0 };
	volatile Collector collector = null;
	volatile Throwable where;
	volatile boolean mark = false;
	volatile boolean recovered = false;
	private boolean phantomizationComplete = true;

	public void setCollector(Collector c) {
		try {
			Locker.start(this);
			if(collector == c)
				return;
			if(c != null)
				c.count.incRef();
			if(collector != null)
				collector.count.decRef();
			collector = c;
		} finally {
			Locker.end();
		}
	}
	static CompLock objectsLock = new CompLock(Priority.GLOBAL,Integer.MAX_VALUE);
	static Set<Sobject> objects = new HashSet<Sobject>();
	volatile static int Sobjectlive =0;
	static Lockable objectsLockable = new Lockable() {

		public CompLock getLock() {
			return objectsLock;
		}

	};

	public Sobject() {
		try {
			Locker.start(objectsLockable);
			objects.add(this);
		} finally {
			Locker.end();
		}
	}

	static void status() {
		Set<Sobject> copy = new HashSet<Sobject>();
		try {
			Locker.start(objectsLockable);
			copy.addAll(objects);
		} finally {
			Locker.end();
		}
		int live = 0;
		for (Sobject so : copy) {
			Locker.start(so);
			assert so.count[0] >= 0 : so.toString();
			assert so.count[1] >= 0 : so.toString();
			assert so.count[2] == 0 : so.toString();
			if(so.deleted) {
				;
			} else {
				assert !so.phantomized;
				assert so.collector == null : so;
			}
			if (!so.deleted)
				live++;
			assert Here.here(so);
			Locker.end();
		}
		assert Here.here("live=" + live); 
		Sobjectlive = live;
	}

	static int live() {
		Set<Sobject> copy = new HashSet<Sobject>();
		try {
			Locker.start(objectsLockable);
			copy.addAll(objects);
		} finally {
			Locker.end();
		}
		int live = 0;
		for (Sobject so : copy) {
			Locker.start(so);
			assert so.count[0] >= 0 : so.toString();
			assert so.count[1] >= 0 : so.toString();
			assert so.count[2] >= 0 : so.toString();
			if (!so.deleted)
				live++;
			Locker.end();
		}
		return live;
	}

	public Sobject get(String field) {
		Sobject ret = null;
		try {
			Locker.start(this);
			assert !deleted;
			Link lk = links.get(field);
			if (lk != null)
				ret = lk.target;
		} finally {
			Locker.end();
		}
		return ret;
	}

	// Pseudo: LinkSet
	public void set(String field, Sobject referent) {
		Link oldLink = null;
		try {
			Locker.start(this, referent);
			oldLink = links.get(field);
			
			if(referent == null) {
				links.remove(field);
				return;
			}
			
			if(oldLink != null && oldLink.target == referent) {
				oldLink = null;
				return;
			}
			assert Here.here(this);

			Link lk = new Link(this);
			links.put(field, lk);
			lk.target = referent;

			if (phantomized) {
				mergeCollectors(referent);
				assert lk.src.collector != null;
				lk.phantomized = true;
				lk.target = referent;
				lk.target.count[2]++;
			} else if (lk.target == this) {
				// self-references must be weak
				lk.which = referent.which.flip();
				lk.target.count[lk.which.value]++;
			} else if (referent.links.size()==0) {
				lk.which = referent.which;
				lk.target.count[lk.which.value]++;
			} else {
				lk.which = referent.which.flip();
				lk.target.count[lk.which.value]++;
			}
			assert Here.here(this);
		} finally {
			Locker.end();
			if (oldLink != null)
				oldLink.dec(false);
		}
	}

	public String toString() {
		try {
			Locker.start(this);
			int cid = collector == null ? -1 : collector.id;
			return id + ":c=[" + count[0] + "," + count[1] + "," + count[2]
					+ "]:w=" + which + " :cptr = " + cid + " :l="
					+ links.size() + ":d=" + (deleted ? "X" : "O")+(mark ? "X" : "O") + ":p="
					+ (phantomized ? "y" : "n") + "|" + links.keySet();
		} finally {
			Locker.end();
		}
	}

	// Pseudo: PhantomizeNode
	void phantomizeNode(Collector cptr) {
		assert Here.here(this);
		boolean phantomize = false;
		List<Entry<String, Link>> phantoms = new ArrayList<Entry<String,Link>>();

		// Determine if merge needs to be done
		Collector c = cptr;
		Collector t = this.collector;
		if(t != null) {
			while(true) {
				try {
					Locker.start(t,c);
					if(c == t)
						break;
					if(c.forward == null && t.forward == null) {
						assert false;
					}
					if(c.forward != null)
						c = c.forward;
					if(t.forward != null)
						t = t.forward;
				} finally {
					Locker.end();
				}
			}
		}

		try {
			Locker.start(this);
			setCollector(this.collector.update());
//			this.collector=collector;
			assert t != null; 
			//setCollector(t);
			assert !deleted;
			count[which.value]--;
			assert count[which.value] >= 0 : toString();
			if (count[which.value] > 0) {
				return;
			} else if (count[which.flip().value] > 0) {
				which = which.flip();
				if (!phantomized) {
					phantomize = true;
					phantomized = true;
					assert collector != null;
					phantomizationComplete = false;
					phantoms.addAll(links.entrySet());
				}
			} else { // phantom count is > 0, but weak and strong are zero
				if (!phantomized) {
					phantomize = true;
					phantomized = true;
					assert collector != null;
					phantomizationComplete = false;
					phantoms.addAll(links.entrySet());
				}
			}
		} finally {
			Locker.end();
		}
		assert Here.here("phantomize=" + phantomize + "/" + phantomized + " => "
				+ this + "/" + phantoms.size());

		if (phantomize) {
			assert Here.here(phantoms.size());
			// Between here and the following syncs
			// the actual contents of the links could
			// be changed. New links could be added
			// and existing links could be redirected
			// or cleared.
			try {
				for (Entry<String,Link> en : phantoms) {
					Link lk = en.getValue();
					assert Here.here("ph=" + lk.target);
					try {
						Locker.start(this, lk.target);
						assert phantomized;
						assert cptr != null;
						if (!lk.phantomized) {
							assert lk.src.phantomized;
							assert lk.src.collector != null;
							lk.phantomize();
							assert Here.here("phantom: " + lk.target);
						}
					} finally {
						assert Here.here("locks remaining="+Locker.currentLocks.get().locks.size());

						Locker.end();
					}
				}
			} finally {
				try {
					Locker.start(this);
					phantomizationComplete = true;
				} finally {
					Locker.end();
				}
			}
		}
	}

	// Pseudo: RecoverNode
	public void recoverNode(SafeList<Sobject> rebuildNext, Collector cptr) {
		List<Entry<String, Link>> rebuild = null;//new ArrayList<>();
		// Determine if merge needs to be done
		Collector c = cptr;
		Collector t = this.collector;
		if(t != null) {
			while(true) {
				try {
					Locker.start(t,c);
					if(c == t)
						break;
					if(c.forward == null && t.forward == null) {
						assert false;
					}
					if(c.forward != null)
						c = c.forward;
					if(t.forward != null)
						t = t.forward;
				} finally {
					Locker.end();
				}
			}
		}
		try {
			Locker.start(this);
			assert !deleted;
			if(collector != null)
				setCollector(collector.update());
			//setCollector(t);
			assert Here.here("rebuild:" + this);
			if (count[which.value] > 0) {
				int wcount = 0;
				while(phantomized && !phantomizationComplete) {
					Locker.end();
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
					}
					Locker.start(this);
					assert wcount++ < 10000;
				}
				assert phantomizationComplete;
				phantomized = false;
				rebuild = new ArrayList<Map.Entry<String,Link>>(links.entrySet());
				assert Here.here("rebuilt " + this);
			} else if (count[which.flip().value] > 0) {
				assert false;
			}
		} finally {
			Locker.end();
		}
		if(rebuild != null) {
			for (Entry<String, Link> e : rebuild) {
				try {
					Link lk = links.get(e.getKey());
					Locker.start(lk.src, lk.target);
					if (lk.phantomized) {
						rebuildLink(rebuildNext, lk);
					}
				} finally {
					Locker.end();
				}
			}
		}
		try {
			Locker.start(this);
			if(count[2] == 0) {
				assert collector == null;
			} else {
				recovered = true;
			}
		} finally {
			Locker.end();
		}
	}

	boolean hasAllPhantomLinks() {
		for(Link lk : links.values()) {
			if(!lk.phantomized)
				return false;
		}
		return true;
	}

	boolean hasAnyPhantomLinks() {
		for(Link lk : links.values()) {
			if(lk.phantomized)
				return true;
		}
		return false;
	}

	private static void rebuildLink(SafeList<Sobject> rebuildNext, final Link lk) {
		assert !lk.src.deleted;
		if (lk.phantomized) {
			if (lk.target == lk.src) {
				lk.which = lk.target.which.flip();
			} else if (lk.target.phantomized) {
				lk.which = lk.target.which;
			} else if(lk.target.links.size()==0) {
				lk.which = lk.target.which;
			} else {
				lk.which = lk.target.which.flip();
			}
			lk.target.count[lk.which.value]++;
			lk.target.count[2]--;
			if(lk.target.count[2] == 0) {// && lk.target.recovered) {
//				lk.target.collector = null;
				lk.target.setCollector(null);
				lk.target.recovered = false;
			}
			lk.phantomized = false;

			if(lk.target.count[lk.target.which.value] == 0 && 
					lk.target.count[lk.target.which.flip().value] > 0) {
				assert lk.target.phantomized : "t="+lk.target.collector.update()+" s="+lk.src.collector.update();
			assert !lk.target.phantomized : "t="+lk.target.collector.update()+" s="+lk.src.collector.update();
			}
			assert lk.target.count[2] >= 0;
			if(lk.target.collector != null)
				rebuildNext.add(lk.target);
		}
	}

	public void incStrong() {
		try {
			Locker.start(this);
			assert !deleted;
			count[which.value]++;
			//			Here.here(this);
		} finally {
			Locker.end();
		}
	}

	public void dec(Bit w) {
		try {
			Locker.start(this);
			assert !deleted;
			count[w.value]--;
			assert count[w.value] >= 0 : toString();
			if (count[w.value] == 0 && w == which) {
				if (count[which.flip().value] == 0 && count[2] == 0) {
					Collector.requestDelete(this);
				} else {
					if (this.collector == null) {
						setCollector(new Collector());
						assert Here.here();
						collector.add(this);
						collector.th = new Xthread(collector);
						assert Here.here();
						collector.th.start();
						assert Here.here("collector created");
					} else {
						//						Here.here(this);
						collector.add(this);
					}
				}
			}

		} finally {
			//			Here.here(this);
			Locker.end();
		}
	}

	// Pseudo: DecPhantom
	public void decPhantom() {
		try {
			Locker.start(this);
			//			Here.here(this);
			assert !deleted;
			count[2]--;
			assert count[2] >= 0;
			if (count[2] == 0) {
				if (count[0] == 0 && count[1] == 0) {
					Collector.requestDelete(this);
				} else {
					setCollector(null);
				}
			}
		} finally {
			Locker.end();
		}
	}

	void decStrong() {
		dec(which);
	}

	void del() {
		try {
			Locker.start(this);
			if(deleted && where != null) {
				where.printStackTrace();
			}
			assert !deleted;
			deleted = true;
			setCollector(null);
			assert Here.here("Deleted " + this);
		} finally {
			Locker.end();
		}
	}

	public void die() {
		boolean hasPhantoms = false;
		List<Link> lks = new ArrayList<Link>();
		try {
			Locker.start(this);
			assert !deleted;
			//			cptrid = null;
			lks.addAll(links.values());
			links.clear();
			assert count[0] == 0;
			assert count[1] == 0;
			if (count[2] > 0)
				hasPhantoms = true;
		} finally {
			Locker.end();
		}
		assert Here.here("die=" + lks.size());
		for (Link lk : lks) {
			lk.free();
		}
		if (!hasPhantoms)
			del();
	}

	public CompLock getLock() {
		return lock;
	}

	public void cleanNode(Collector c) {
		boolean die = false;
		try {
			Locker.start(this);

			assert Here.here("on clean-up " + this);
			assert (count[0] >= 0);
			assert (count[1] >= 0);
			if (count[0] == 0 && count[1] == 0) {
				die = true;
			}
		} finally {
			Locker.end();
		}
		if(die) 
			die();
		try {
			Locker.start(this);
			decPhantom();
		} finally {
			Locker.end();
		}
	}

	/**
	 * Returns true if a merge
	 * really happened.
	 **/
	public boolean mergeCollectors(Sobject target) {
		// Pseudo: MergeCollectors
		Collector s = collector;
		Collector t = target.collector;
		if(t == null && s != null) {
			target.setCollector(s);
		}
		if(s == null && t != null) {
			setCollector(t);
		}
		if(t == s || t == null || s == null)
			return false;
		assert t != null;
		assert s != null;
		while (true) {
			try {
				Locker.start(t, s, this, target);
				t.checkTerminated();
				s.checkTerminated();
				if(t.forward == s && s.forward == null) {
//					target.collector = this.collector = s;
					target.setCollector(s);
					this.setCollector(s);
					return false;
				} else if(s.forward == t && t.forward == null) {
//					target.collector = this.collector = t;
					target.setCollector(t);
					this.setCollector(t);
					return false;
				} else if (t.forward != null) {
					t = t.forward;
				} else if(s.forward != null) {
					s = s.forward;
				} else if(s == t) {
					return false;
				} else if(s != t) {
					assert s.forward == null;
					assert t.forward == null;
					// We could do this the other way around
					t.merge(s);
					assert s.forward == t;
//					target.collector = this.collector = t;
					target.setCollector(t);
					this.setCollector(t);
					return true;
				}
			} finally {
				assert t != null;
				Locker.end();
			}
		}

	}
}
