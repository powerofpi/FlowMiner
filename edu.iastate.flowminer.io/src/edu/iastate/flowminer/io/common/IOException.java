package edu.iastate.flowminer.io.common;

public class IOException extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 662142161778043059L;

	public IOException() {
		super();
	}

	public IOException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public IOException(String message, Throwable cause) {
		super(message, cause);
	}

	public IOException(String message) {
		super(message);
	}

	public IOException(Throwable cause) {
		super(cause);
	}
}
