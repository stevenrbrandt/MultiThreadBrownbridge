package gc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * A list that can only be accessed with Locks.
 * 
 * @author sbrandt
 *
 * @param <T>
 */
public class SafeList<T> {
	final WrappedLock lock = new WrappedLock(Priority.LIST);
	final Counter count;
	
	public SafeList(Counter c) {
		count = c;
	}
	
	final List<T> data = new ArrayList<T>();
	
	public void add(T datum) {
		SafeList<T> f = null;
		try {
			Locker.start(lock);
			if(forward_ == null) {
				count.incStore();
				data.add(datum);
			} else {
				f = forward_;
			}
		} finally {
			Locker.end();
		}
		if(f != null) {
			while(f.forward_ != null)
				f = f.forward_;
			f.add(datum);
		}
	}

//	public Iterator<T> iterator() {
//		return new Iterator<T>() {
//			int index = 0;
//			
//			public boolean hasNext() {
//				try {
//					Locker.start(lock);
////					assert index <= data.size();
//					return index < data.size();
//				} finally {
//					Locker.end();
//				}
//			}
//
//			public T next() {
//				try {
//					Locker.start(lock);
//					if(index >= data.size())
//						return null;
//					return data.get(index++);
//				} finally {
//					Locker.end();
//				}
//			}
//
//			public void remove() {
//				throw new Error();
//			}
//		};
//	}

	public int size() {
		try {
			Locker.start(lock);
			return data.size();
		} finally {
			Locker.end();
		}
	}

	public boolean isEmpty() {
		return size() == 0;
	}

	public T poll() {
		try {
			Locker.start(lock);
			if(data.size()==0)
				return null;
			count.decStore();
			return data.remove(0);
		} finally {
			Locker.end();
		}
	}

//	public void clear() {
//		try {
//			Locker.start(lock);
//			while(!isEmpty())
//				poll();
//		} finally {
//			Locker.end();
//		}
//	}

	public Lockable getLock() {
		return lock;
	}

	public Collection<? extends T> collect() {
		return data;
	}

	private volatile SafeList<T> forward_;
	
	public void forward(SafeList<T> forwardTo) {
		try {
			Locker.start(getLock(),forwardTo.getLock());
//			forwardTo.data.addAll(data);
//			clear();
			while(true) {
				T obj = poll();
				if(obj == null)
					break;
				forwardTo.add(obj);
			}
			forward_ = forwardTo;
		} finally {
			Locker.end();
		}
	}

	public boolean forwardsTo(SafeList<T> forward) {
		return forward_ == forward;
	}
}
