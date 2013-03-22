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
 *
 * As a general note: A state value here is always only a cached version that may already be incorrect.
 * Even if no one else is controlling the lights. I've observed that e.g. the brightness value changes if a light is just switched on.
 *
 * @author Stephan Jaetzold <p><small>Created at 20.03.13, 14:59</small> */
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

	int autoSyncInterval = 1000;
	long lastSyncTime;

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

	public int getAutoSyncInterval() {
		return autoSyncInterval;
	}

	public void setAutoSyncInterval(int autoSyncInterval) {
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

	public boolean isOn() {
		checkSync();
		return on;
	}

	@Override
	public void setOn(boolean on) {
		stateChange("on", on);
		this.on = on;
	}

	public int getBrightness() {
		checkSync();
		return brightness;
	}

	@Override
	public void setBrightness(int brightness) {
		if(brightness<0 || brightness>255) {
			throw new IllegalArgumentException("Brightness must be between 0-255");
		}
		stateChange("bri", brightness);
		this.brightness = brightness;
	}

	public int getHue() {
		checkSync();
		return hue;
	}

	@Override
	public void setHue(int hue) {
		if(hue<0 || hue>65535) {
			throw new IllegalArgumentException("Hue must be between 0-65535");
		}
		stateChange("hue", hue);
		this.hue = hue;
		colorMode = ColorMode.HS;
	}

	public int getSaturation() {
		checkSync();
		return saturation;
	}

	@Override
	public void setSaturation(int saturation) {
		if(saturation<0 || saturation>255) {
			throw new IllegalArgumentException("Saturation must be between 0-255");
		}
		stateChange("sat", saturation);
		this.saturation = saturation;
		colorMode = ColorMode.HS;
	}

	public double getCiex() {
		checkSync();
		return ciex;
	}

	public void setCiex(double ciex) {
		setCieXY(ciex, ciey);
		this.ciex = ciex;
	}

	public double getCiey() {
		checkSync();
		return ciey;
	}

	public void setCiey(double ciey) {
		setCieXY(ciex, ciey);
		this.ciey = ciey;
	}

	@Override
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
		checkSync();
		return colorTemperature;
	}

	@Override
	public void setColorTemperature(int colorTemperature) {
		if(colorTemperature<153 || colorTemperature>500) {
			throw new IllegalArgumentException("ColorTemperature must be between 153-500");
		}
		stateChange("ct", colorTemperature);
		this.colorTemperature = colorTemperature;
		colorMode = ColorMode.CT;
	}

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
		return bridge.checkedSuccessRequest(PUT, "/lights/" + getId() + "/state", json);
	}

	private List<JSONObject> stateChange(String param, Object value) {
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
		if(autoSyncInterval>0 && now-lastSyncTime>autoSyncInterval) {
			sync();
		}
	}

	private ThreadLocal<Boolean> syncing = new ThreadLocal<>();
	private void sync() {
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
