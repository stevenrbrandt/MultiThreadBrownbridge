package gc;

/**
 * A useful debugging class.
 * It returns true so that it is possible to write
 * <pre>
 * 	assert Here.here("Message");
 * </pre>
 * and enable messages only when assert is enabled.
 * 
 * @author sbrandt
 *
 */
public class Here {
	public static boolean here() {
		StackTraceElement[] elems = new Throwable().getStackTrace();
		System.out.println("here: " + elems[1]);
		return true;
	}

	public static boolean here(Object msg) {
		StackTraceElement[] elems = new Throwable().getStackTrace();
		System.out.println("here: " + elems[1] + ": " + msg);
		return true;
	}
}
