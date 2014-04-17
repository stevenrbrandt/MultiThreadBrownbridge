package gc;

public class Link {
	Bit which;
	boolean phantomized;
	volatile Sobject target;
	final Sobject src;

	public Link(Sobject s) {
		src = s;
	}

	public void free() {
		assert Here.here(src + " => " + target);
		try {
			Locker.start(src);
			if (target == null)
				return;
		} finally {
			Locker.end();
		}
		dec(false);
		assert Here.here("after=" + target);
		target = null;
	}

	// Pseudo: PhantomizeLink
	public void phantomize() {
		try {
			Locker.start(target, src);
			if (target == null)
				return;
			if (phantomized)
				return;
			target.count[2]++;
			dec(false);
			assert target.collector != null || src.collector != null;
			src.mergeCollectors(target);
			phantomized = true;
			assert target.collector.equals(src.collector) : "diff1: t="+target.collector+" s="+src.collector;
		} finally {
			Locker.end();
			assert Here.here("locks remaining="
					+ Locker.currentLocks.get().locks.size());
		}
	}

	public void dec(boolean phantomizing) {
		if(target == null)
			return;
		if(phantomizing) {
			decPhantomizing();
			return;
		}
		// Pseudo: LinkFree
		if(target == null)
			return;
		try {
			Locker.start(target, src);
			if (phantomized) {
				target.decPhantom();
			} else {
				target.dec(which);
			} 
		} finally {
			Locker.end();
			assert Here.here("locks remaining="
					+ Locker.currentLocks.get().locks.size());

		}
	}

	private void decPhantomizing() {
		boolean merge = false;
		assert false;
		try {
			Locker.start(target, src);
			if (phantomized) {
				target.decPhantom();
			} else {
				int ncount = --target.count[which.value];

				if(ncount == 0 && target.count[which.flip().value] == 0 && target.count[2] == 0) {
					Collector.requestDelete(target);
					return;
				}
				assert target.count[which.value] >= 0 : target.toString();

				merge = false;
				if(ncount == 0 && which == target.which) {
					target.collector.add(target);
				}
			}
		} finally {
			Locker.end();
			assert Here.here("locks remaining="
					+ Locker.currentLocks.get().locks.size());

		}
		assert Here.here("until now");
		if (merge) {
			src.mergeCollectors(target);
		}
	}
}
