package de.jaetzold.philips.hue;

import org.json.JSONObject;

/** @author Stephan Jaetzold <p><small>Created at 21.03.13, 18:39</small> */
public class HueCommException extends RuntimeException {
	public final JSONObject error;

	public HueCommException(JSONObject error) {
		super(error.optString("description"));
		this.error = error;
	}

	public HueCommException(Throwable cause) {
		super("Exception", cause);
		error = new JSONObject();
		error.put("description", cause!=null ? cause.getMessage() : "");
	}

	public HueCommException(String message, Throwable cause) {
		super(message, cause);
		error = new JSONObject();
		error.put("description", cause!=null ? cause.getMessage() : "");
	}

	public HueCommException(String message, JSONObject error) {
		super(message);
		this.error = error;
	}

	public HueCommException(String message) {
		super(message);
		error = new JSONObject();
		error.put("description", message);
	}
}
