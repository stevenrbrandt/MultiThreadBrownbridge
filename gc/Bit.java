package gc;

/**
 * Keeps track of the state of the "which" bit.
 * 
 * @author sbrandt
 *
 */
public enum Bit {
	ZERO(0), ONE(1);
	public final int value;

	Bit(int v) {
		value = v;
	}

	public Bit flip() {
		switch (this) {
		case ZERO:
			return ONE;
		default:
			return ZERO;
		}
	}

	public Bit valueOf(int n) {
		switch (n) {
		case 0:
			return ZERO;
		case 1:
			return ONE;
		default:
			throw new Error();
		}
	}
}
