package gc;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class Root {
	Sobject ref = null;
	static ArrayList<Sobject> roots = new ArrayList<Sobject>();

	public void alloc() {
		if (ref != null)
			ref.decStrong();
		ref = new Sobject();
		ref.incStrong();
		synchronized (Root.class) {
			roots.add(ref);
		}

	}

	public void set(Sobject so) {
		try {
			Locker.start(so, ref);
			if (so != null) {
				so.incStrong();
			}
			if (ref != null) {
				ref.decStrong();
			}
			synchronized (Root.class){
			roots.remove(ref);
			if(so!=null)
				roots.add(so);
			}
			ref = so;
		} finally {
			Locker.end();
		}
	}

	public void free() {
		assert Here.here();
		set(null);
	}

	public Sobject get() {
		return ref;
	}

	public Sobject get(String field) {
		if (ref == null)
			return null;
		return ref.get(field);
	}

	public void set(String field, Sobject obj) {
		ref.set(field, obj);
	}
}
