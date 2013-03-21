package com.jvmcode.philips.hue;

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
		this.name = name;
	}

	public boolean isOn() {
		return on;
	}

	public void setOn(boolean on) {
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
}
