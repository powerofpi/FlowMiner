package edu.iastate.flowminer.exception;

/**
 * If thrown, indicates an error encountered during summary mining or application.
 * @author tdeering
 *
 */
public class FlowMinerException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8455105242886433134L;

	public FlowMinerException(String msg){
		super(msg);
	}
	
	public FlowMinerException(String msg, Throwable cause){
		super(msg, cause);
	}
}
