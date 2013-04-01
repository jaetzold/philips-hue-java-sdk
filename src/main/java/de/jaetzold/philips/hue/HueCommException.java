package de.jaetzold.philips.hue;

/*
 Copyright (c) 2013 Stephan Jaetzold.

 Licensed under the Apache License, Version 2.0 (the "License");
 You may not use this file except in compliance with the License.
 You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and limitations under the License.
 */

import org.json.JSONObject;

/**
 * An Exception thrown by calls to the hue API when something regarding the communication with the bridge device is not right.
 * It always contains a {@link JSONObject} describing the error. This may be the result of a (failed) request to the bridge device.
 * If there is no actual request that returned such an error object one is created with a 'description' property.
 *
 * @author Stephan Jaetzold <p><small>Created at 21.03.13, 18:39</small>
 */
@SuppressWarnings("UnusedDeclaration")
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
