package de.jaetzold.philips.hue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;

import java.util.Arrays;
import java.util.List;

import static de.jaetzold.philips.hue.HueBridgeComm.RM.*;
import static de.jaetzold.philips.hue.HueLight.ColorMode.*;

/**
 * This class represents a single light bulb. Use it to query or manipulate the state of a single light bulb.
 * <p>
 *     An instance of this class is not created directly. Instead query a {@link HueBridge} for its lights using either
 *     {@link HueBridge#getLights()} or {@link HueBridge#getLight(Integer)}.
 * </p>
 * <p>
 *     When querying for state the actual value is automatically updated with the current value on the bridge if its local cache is 'too old'.
 *     This behaviour can be tuned (or turned off) using {@link #setAutoSyncInterval(Integer)}.
 * </p>
 *
 * As a general note: A state value here is always only a cached version that may already be incorrect.
 * Even if no one else is controlling the lights. I've observed that e.g. the brightness value changes if a light is just switched on.
 *
 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html">Philips hue API, Section 1</a> for further reference.</p>
 *
 * @author Stephan Jaetzold <p><small>Created at 20.03.13, 14:59</small>
 */
public class HueLightBulb implements HueLight {
	final Integer id;
	final HueBridge bridge;

	String name;

	boolean on;
	int brightness;
	int hue;
	int saturation;
	double ciex;
	double ciey;
	int colorTemperature;
	Effect effect;

	ColorMode colorMode;
	Integer transitionTime;

	Integer autoSyncInterval = 1000;
	long lastSyncTime;

	/**
	 * This constructor is package private, since lights are not to be created. A {@link HueBridge} is queried for them.
	 */
	HueLightBulb(HueBridge bridge, Integer id) {
		if(id==null || id<0) {
			throw new IllegalArgumentException("id has to be non-negative and non-null");
		}
		if(bridge==null) {
			throw new IllegalArgumentException("bridge may not be null");
		}
		this.bridge = bridge;
		this.id = id;
	}

	@Override
	public Integer getId() {
		return id;
	}

	@Override
	public HueBridge getBridge() {
		return bridge;
	}

	@Override
	public Integer getTransitionTime() {
		return transitionTime;
	}

	@Override
	public void setTransitionTime(Integer transitionTime) {
		this.transitionTime = transitionTime;
	}

	/**
	 * The time in milliseconds that a queried state value may be old before it needs to be updated with the current value from the bridge device.
	 *
	 * @return null, if values are never automatically updated with the current state from the bridge device.
	 */
	@SuppressWarnings("UnusedDeclaration")
	public Integer getAutoSyncInterval() {
		return autoSyncInterval;
	}

	/**
	 * Set the time in milliseconds that a queried state value may be old before it needs to be updated with the current value from the bridge device.
	 * This may be null if not automatic syncing should take place. Manual syncing can be done by calling {@link #sync()}.
	 * The default value is 1000 milliseconds.
	 *
	 * @param autoSyncInterval The time in milliseconds that a cached state value is used until it is updated again.
	 */
	public void setAutoSyncInterval(Integer autoSyncInterval) {
		this.autoSyncInterval = autoSyncInterval;
	}

	@Override
	public String getName() {
		checkSync();
		return name;
	}

	@Override
	public void setName(String name) {
		if(name==null || name.trim().length()>32) {
			throw new IllegalArgumentException("Name (without leading or trailing whitespace) has to be less than 32 characters long");
		}
		final JSONObject response = bridge.checkedSuccessRequest(PUT, "/lights/" +id, JO().key("name").value(name.trim())).get(0);
		final String actualName = response.getJSONObject("success").optString("/lights/" + id + "/name");
		this.name = actualName!=null ? actualName : name;
	}

	/**
	 * Whether this light is in 'on' or 'off' state.
	 *
	 * @return true if this light is on, false otherwise.
	 */
	public boolean isOn() {
		checkSync();
		return on;
	}

	/**
	 * Whether this light is in 'on' or 'off' state.
	 *
	 * @return true if this light is on, false otherwise.
	 */
	@SuppressWarnings("UnusedDeclaration")
	public Boolean getOn() {
		return isOn();
	}

	@Override
	public void setOn(Boolean on) {
		stateChange("on", on);
		this.on = on;
	}

	/**
	 * Get the brightness of this light.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#14_get_light_attributes_and_state">Philips hue API, Section 1.4</a> for further reference.</p>
	 *
	 * @return the brightness value between 0 (lowest that is not off) and 255 (highest)
	 */
	public Integer getBrightness() {
		checkSync();
		return brightness;
	}

	@Override
	public void setBrightness(Integer brightness) {
		if(brightness<0 || brightness>255) {
			throw new IllegalArgumentException("Brightness must be between 0-255");
		}
		stateChange("bri", brightness);
		this.brightness = brightness;
	}

	/**
	 * Get the hue of this light. Note that the validity of this value may depend on {@link #getColorMode()}.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#14_get_light_attributes_and_state">Philips hue API, Section 1.4</a> for further reference.</p>
	 *
	 * @see #HUE_RED
	 * @see #HUE_GREEN
	 * @see #HUE_BLUE
	 *
	 * @return the hue value between 0 and 65535 (both red)
	 */
	public Integer getHue() {
		checkSync();
		return hue;
	}

	@Override
	public void setHue(Integer hue) {
		if(hue<0 || hue>65535) {
			throw new IllegalArgumentException("Hue must be between 0-65535");
		}
		stateChange("hue", hue);
		this.hue = hue;
		colorMode = ColorMode.HS;
	}

	/**
	 * Get the saturation of this light. Note that the validity of this value may depend on {@link #getColorMode()}.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#14_get_light_attributes_and_state">Philips hue API, Section 1.4</a> for further reference.</p>
	 *
	 * @return the saturation value between 0 (white) and 255 (colored)
	 */
	public Integer getSaturation() {
		checkSync();
		return saturation;
	}

	@Override
	public void setSaturation(Integer saturation) {
		if(saturation<0 || saturation>255) {
			throw new IllegalArgumentException("Saturation must be between 0-255");
		}
		stateChange("sat", saturation);
		this.saturation = saturation;
		colorMode = ColorMode.HS;
	}

	/**
	 * Get the x coordinate in CIE color space of this light. Note that the validity of this value may depend on {@link #getColorMode()}.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#14_get_light_attributes_and_state">Philips hue API, Section 1.4</a> for further reference.</p>
	 *
	 * @return the x coordinate in CIE color space between 0 and 1
	 */
	public Double getCiex() {
		checkSync();
		return ciex;
	}

	/**
	 * Set the x coordinate of a color in CIE color space. For y the currently cached value is used.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a> for further reference.</p>
	 *
	 * @param ciex the x coordinate in CIE color space between 0 and 1
	 */
	@SuppressWarnings("UnusedDeclaration")
	public void setCiex(Double ciex) {
		setCieXY(ciex, ciey);
		this.ciex = ciex;
	}

	/**
	 * Get the y coordinate in CIE color space of this light. Note that the validity of this value may depend on {@link #getColorMode()}.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#14_get_light_attributes_and_state">Philips hue API, Section 1.4</a> for further reference.</p>
	 *
	 * @return the y coordinate in CIE color space between 0 and 1
	 */
	public Double getCiey() {
		checkSync();
		return ciey;
	}

	/**
	 * Set the y coordinate of a color in CIE color space. For x the currently cached value is used.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a> for further reference.</p>
	 *
	 * @param ciey the y coordinate in CIE color space between 0 and 1
	 */
	@SuppressWarnings("UnusedDeclaration")
	public void setCiey(Double ciey) {
		setCieXY(ciex, ciey);
		this.ciey = ciey;
	}

	@Override
	public void setCieXY(Double ciex, Double ciey) {
		if(ciex<0 || ciex>1 || ciey<0 || ciey>1) {
			throw new IllegalArgumentException("A cie coordinate must be between 0.0-1.0");
		}
		stateChange("xy", new JSONArray(Arrays.asList(ciex.floatValue(),ciey.floatValue())));
		this.ciex = ciex;
		this.ciey = ciey;
		colorMode = ColorMode.XY;
	}

	/**
	 * Get the mired color temperature of this light. Note that the validity of this value may depend on {@link #getColorMode()}.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#14_get_light_attributes_and_state">Philips hue API, Section 1.4</a> for further reference.</p>
	 *
	 * @return the color temperature value in mired between 153 (6500K) and 500 (2000K)
	 */
	public Integer getColorTemperature() {
		checkSync();
		return colorTemperature;
	}

	@Override
	public void setColorTemperature(Integer colorTemperature) {
		if(colorTemperature<153 || colorTemperature>500) {
			throw new IllegalArgumentException("ColorTemperature must be between 153-500");
		}
		stateChange("ct", colorTemperature);
		this.colorTemperature = colorTemperature;
		colorMode = ColorMode.CT;
	}

	/**
	 * Get the current dynamic effect of this light.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#14_get_light_attributes_and_state">Philips hue API, Section 1.4</a> for further reference.</p>
	 *
	 * @return the current dynamic effect of this light.
	 */
	public Effect getEffect() {
		checkSync();
		return effect;
	}

	@Override
	public void setEffect(Effect effect) {
		stateChange("effect", effect.name);
		this.effect = effect;
	}

	@Override
	public void setAlert(Alert alert) {
		stateChange("alert", alert.name);
	}

	/**
	 * Get the mode with which the current color of the light has been set.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#14_get_light_attributes_and_state">Philips hue API, Section 1.4</a> for further reference.</p>
	 *
	 * @return The current mode with which the current color of the light has been set.
	 */
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
			   + ","
			   +"BRI:" +getBrightness()
			   +(getEffect()!=Effect.NONE ? ","+getEffect() : "")
			   +"]";
	}

	@Override
	public void stateChangeTransaction(Integer transitionTime, Runnable changes) {
		openStateChangeTransaction(transitionTime);
		try {
			try {
				changes.run();
			} catch(Throwable t) {
				stateTransactionJson.set(null);
				//noinspection ThrowCaughtLocally
				throw t;
			} finally {
				commitStateChangeTransaction();
			}
		} catch(Throwable t) {
			// do kind of rollback by syncing the state from the bridge
			sync();
		}
	}

	/**
	 * Update the local state cache with values from the bridge device.
	 */
	public void sync() {
		if(syncing.get() == null || !syncing.get()) {
			try {
				syncing.set(true);
				final JSONObject response = bridge.request(GET, "/lights/" + getId(), "").get(0);
				if(response.has("error")) {
					throw new HueCommException(response.getJSONObject("error"));
				} else {
					parseLight(response);
				}
			} finally {
				syncing.set(false);
			}
		}
	}

	// *****************************************
	// Implementation internal methods
	// *****************************************

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
		if(json!=null) {
			return bridge.checkedSuccessRequest(PUT, "/lights/" + getId() + "/state", json);
		} else {
			return null;
		}
	}

	private List<JSONObject> stateChange(String param, Object value) {
		if(value==null) {
			throw new IllegalArgumentException("A value of null is not allowed for any of the lights states");
		}
		final JSONObject stateTransaction = stateTransactionJson.get();
		if(stateTransaction==null) {
			JSONWriter json = JO();
			if(transitionTime!=null) {
				json = json.key("transitiontime").value(transitionTime);
			}
			return bridge.checkedSuccessRequest(PUT, "/lights/" + getId() + "/state", json.key(param).value(value));
		} else {
			stateTransaction.put(param, value);
			return null;
		}
	}

	private void checkSync() {
		final long now = System.currentTimeMillis();
		if(autoSyncInterval!=null && now-lastSyncTime>autoSyncInterval) {
			sync();
		}
	}

	private ThreadLocal<Boolean> syncing = new ThreadLocal<>();

	void parseLight(JSONObject lightJson) {
		name = lightJson.getString("name");

		if(lightJson.has("state")) {
			final JSONObject state = lightJson.getJSONObject("state");
			on = state.getBoolean("on");
			brightness = state.getInt("bri");
			hue = state.getInt("hue");
			saturation = state.getInt("sat");
			ciex = state.getJSONArray("xy").getDouble(0);
			ciey = state.getJSONArray("xy").getDouble(1);
			colorTemperature = state.getInt("ct");
			colorMode = new ColorMode[]{HS,XY,CT}[Arrays.asList("hs", "xy", "ct").indexOf(state.getString("colormode").toLowerCase())];
			final Effect effect = Effect.fromName(state.getString("effect"));
			if(effect==null) {
				throw new HueCommException("Can not find effect named \"" +state.getString("effect") +"\"");
			}
			this.effect = effect;

			lastSyncTime = System.currentTimeMillis();
		} else {
			sync();
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
