package com.jvmcode.philips.hue;

import org.json.JSONObject;
import org.json.JSONStringer;

import java.util.List;

import static com.jvmcode.philips.hue.HueHubComm.RM.PUT;

/** @author Stephan Jaetzold <p><small>Created at 20.03.13, 14:59</small> */
public class HueLightBulb {
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
		hub.checkedSuccessRequest(PUT, "/lights/" +id +"/state", JO().key("on").value(on)).get(0);
		this.on = on;
	}

	public int getBrightness() {
		return brightness;
	}

	public void setBrightness(int brightness) {
		this.brightness = brightness;
	}

	public int getHue() {
		return hue;
	}

	public void setHue(int hue) {
		this.hue = hue;
	}

	public int getSaturation() {
		return saturation;
	}

	public void setSaturation(int saturation) {
		this.saturation = saturation;
	}

	public double getCiex() {
		return ciex;
	}

	public void setCiex(double ciex) {
		this.ciex = ciex;
	}

	public double getCiey() {
		return ciey;
	}

	public void setCiey(double ciey) {
		this.ciey = ciey;
	}

	public int getColorTemperature() {
		return colorTemperature;
	}

	public void setColorTemperature(int colorTemperature) {
		this.colorTemperature = colorTemperature;
	}

	public ColorMode getColorMode() {
		return colorMode;
	}

	@Override
	public String toString() {
		return id +"(" +name +")" +"["
			   +(on ? "ON" : "OFF") +","
			   +(colorMode==ColorMode.CT ? "CT:"+colorTemperature : "")
			   +(colorMode==ColorMode.HS ? "HS:"+hue +"/" +saturation : "")
			   +(colorMode==ColorMode.XY ? "XY:"+ciex +"/" +ciey : "")
			   +"]";
	}

	/**
	 * Helper method to shorten creation of a JSONObject String.
	 * @return A JSONStringer with an object already 'open' and auto-object-end on a call to toString()
	 */
	private static JSONStringer JO() {
		return new JSONStringer() {
			boolean endObjectDone = false;
			{ object(); }
			@Override
			public String toString() {
				if(!endObjectDone) {
					endObject();
				}
				return super.toString();
			}
		};
	}
}
