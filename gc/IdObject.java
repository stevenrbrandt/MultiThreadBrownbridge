package gc;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An IdObject has a unique integer id.
 * 
 * @author sbrandt
 *
 */
public class IdObject {
	private final static AtomicInteger seq = new AtomicInteger(1);
	public final int id = seq.getAndIncrement();

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof IdObject) {
			return o.hashCode() == hashCode();
		} else {
			return false;
		}
	}

	public String toString() {
		return "Obj=" + id;
	}
}
