package org.bp.bookstore.exceptions;

public class DeliveryException extends Exception {

	public DeliveryException() {
	}

	public DeliveryException(String message) {
		super(message);
	}

	public DeliveryException(Throwable cause) {
		super(cause);
	}

	public DeliveryException(String message, Throwable cause) {
		super(message, cause);
	}

	public DeliveryException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
