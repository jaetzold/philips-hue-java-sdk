package de.jaetzold.philips.hue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;

import java.util.Arrays;
import java.util.List;

import static de.jaetzold.philips.hue.HueHubComm.RM.PUT;

/** @author Stephan Jaetzold <p><small>Created at 20.03.13, 14:59</small> */
public class HueLightBulb {
	public static final int HUE_RED = 0;
	public static final int HUE_RED_2 = 65535;
	public static final int HUE_GREEN = 25500;
	public static final int HUE_BLUE = 46920;

	final Integer id;
	final HueHub hub;

	String name;

	boolean on;
	int brightness;
	int hue;
	int saturation;
	double ciex;
	double ciey;
	int colorTemperature;
	ColorMode colorMode;
	Integer transitionTime;

	public static enum ColorMode {
		HS,CT,XY
	}

	HueLightBulb(HueHub hub, Integer id) {
		if(id==null || id<0) {
			throw new IllegalArgumentException("id has to be non-negative and non-null");
		}
		if(hub==null) {
			throw new IllegalArgumentException("hub may not be null");
		}
		this.hub = hub;
		this.id = id;
	}

	public Integer getId() {
		return id;
	}

	public HueHub getHub() {
		return hub;
	}

	public Integer getTransitionTime() {
		return transitionTime;
	}

	public void setTransitionTime(Integer transitionTime) {
		this.transitionTime = transitionTime;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if(name==null || name.trim().length()>32) {
			throw new IllegalArgumentException("Name (without leading or trailing whitespace) has to be less than 32 characters long");
		}
		final JSONObject response = hub.checkedSuccessRequest(PUT, "/lights/" +id, JO().key("name").value(name)).get(0);
		final String actualName = response.getJSONObject("success").optString("/lights/" + id + "/name");
		this.name = actualName!=null ? actualName : name;
	}

	public boolean isOn() {
		return on;
	}

	public void setOn(boolean on) {
		stateChange("on", on);
		this.on = on;
	}

	public int getBrightness() {
		return brightness;
	}

	public void setBrightness(int brightness) {
		if(brightness<0 || brightness>255) {
			throw new IllegalArgumentException("Brightness must be between 0-255");
		}
		stateChange("bri", brightness);
		this.brightness = brightness;
	}

	public int getHue() {
		return hue;
	}

	public void setHue(int hue) {
		if(hue<0 || hue>65535) {
			throw new IllegalArgumentException("Hue must be between 0-65535");
		}
		stateChange("hue", hue);
		this.hue = hue;
		colorMode = ColorMode.HS;
	}

	public int getSaturation() {
		return saturation;
	}

	public void setSaturation(int saturation) {
		if(saturation<0 || saturation>255) {
			throw new IllegalArgumentException("Saturation must be between 0-255");
		}
		stateChange("sat", saturation);
		this.saturation = saturation;
		colorMode = ColorMode.HS;
	}

	public double getCiex() {
		return ciex;
	}

	public void setCiex(double ciex) {
		setCieXY(ciex, ciey);
		this.ciex = ciex;
	}

	public double getCiey() {
		return ciey;
	}

	public void setCiey(double ciey) {
		setCieXY(ciex, ciey);
		this.ciey = ciey;
	}

	public void setCieXY(double ciex, double ciey) {
		if(ciex<0 || ciex>1 || ciey<0 || ciey>1) {
			throw new IllegalArgumentException("A cie coordinate must be between 0.0-1.0");
		}
		stateChange("xy", new JSONArray(Arrays.asList((float)ciex,(float)ciey)));
		this.ciex = ciex;
		this.ciey = ciey;
		colorMode = ColorMode.XY;
	}

	public int getColorTemperature() {
		return colorTemperature;
	}

	public void setColorTemperature(int colorTemperature) {
		if(colorTemperature<153 || colorTemperature>500) {
			throw new IllegalArgumentException("ColorTemperature must be between 153-500");
		}
		stateChange("ct", colorTemperature);
		this.colorTemperature = colorTemperature;
		colorMode = ColorMode.CT;
	}

	public ColorMode getColorMode() {
		return colorMode;
	}

	@Override
	public String toString() {
		return getId() +"(" +getName() +")" +"["
			   +(isOn() ? "ON" : "OFF") +","
			   +(getColorMode()==ColorMode.CT ? "CT:"+getColorTemperature() : "")
			   +(getColorMode()==ColorMode.HS ? "HS:"+getHue() +"/" +getSaturation() : "")
			   +(getColorMode()==ColorMode.XY ? "XY:"+getCiex() +"/" +getCiey() : "")
			   +"]";
	}

	// Beware: This only changes the state of _this_ light in bulk. Any other light changed by the runnable is change directly
	// If more lights should be changed in one transaction it is possible by nesting runnables that call stateChangeTransaction on other lights.
	// transactions on the same light can not be nested. This will result in an exception.
	public void stateChangeTransaction(Integer transitionTime, Runnable changes) {
		openStateChangeTransaction(transitionTime);
		try {
			changes.run();
		} catch(Throwable t) {
			stateTransactionJson.set(null);
			throw t;
		} finally {
			commitStateChangeTransaction();
		}
	}

	private ThreadLocal<JSONObject> stateTransactionJson = new ThreadLocal<>();
	private void openStateChangeTransaction(Integer transitionTime) {
		if(stateTransactionJson.get()==null) {
			stateTransactionJson.set(new JSONObject());
			if(transitionTime!=null) {
				stateTransactionJson.get().put("transitiontime", transitionTime);
			}
		} else {
			throw new IllegalStateException("Have an open state change transaction already");
		}
	}

	private List<JSONObject> commitStateChangeTransaction() {
		final JSONObject json = stateTransactionJson.get();
		stateTransactionJson.set(null);
		return hub.checkedSuccessRequest(PUT, "/lights/" + getId() + "/state", json);
	}

	private List<JSONObject> stateChange(String param, Object value) {
		final JSONObject stateTransaction = stateTransactionJson.get();
		if(stateTransaction==null) {
			JSONWriter json = JO();
			if(transitionTime!=null) {
				json = json.key("transitiontime").value(transitionTime);
			}
			return hub.checkedSuccessRequest(PUT, "/lights/" + getId() + "/state", json.key(param).value(value));
		} else {
			stateTransaction.put(param, value);
			return null;
		}
	}

	/**
	 * Helper method to shorten creation of a JSONObject String.
	 * @return A JSONStringer with an object already 'open' and auto-object-end on a call to toString()
	 */
	private static JSONStringer JO() {
		return new JSONStringer() {
			{ object(); }
			@Override
			public String toString() {
				return writer.toString()+(mode!='d' ? "}" :"");
			}
		};
	}
}
