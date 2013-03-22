package de.jaetzold.philips.hue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONWriter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static de.jaetzold.philips.hue.HueBridgeComm.RM.PUT;

/**
 *
 * Note: Group creation and deletion is not supported as of Philips Hue API version 1.0.
 * So effectively only the implicit group 0 containing all the lights can be used.
 *
 * @author Stephan Jaetzold <p><small>Created at 22.03.13, 14:10</small> */
public class HueLightGroup implements HueLight {
	final Integer id;
	final HueBridge bridge;

	String name;
	final Map<Integer, HueLightBulb> lights;

	Integer transitionTime;

	public HueLightGroup(HueBridge bridge, Integer id) {
		this(bridge, id, null);
	}

	HueLightGroup(HueBridge bridge, Integer id, Map<Integer, HueLightBulb> lights) {
		if(id==null || id<0) {
			throw new IllegalArgumentException("id has to be non-negative and non-null");
		}
		if(bridge==null) {
			throw new IllegalArgumentException("bridge may not be null");
		}
		this.bridge = bridge;
		this.id = id;
		if(lights!=null) {
			this.lights = lights;
		} else {
			this.lights = new TreeMap<>();
		}
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

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		if(name==null || name.trim().length()>32) {
			throw new IllegalArgumentException("Name (without leading or trailing whitespace) has to be less than 32 characters long");
		}
		final JSONObject response = bridge.checkedSuccessRequest(PUT, "/lights/" +id, JO().key("name").value(name)).get(0);
		final String actualName = response.getJSONObject("success").optString("/lights/" + id + "/name");
		this.name = actualName!=null ? actualName : name;
	}

	public Collection<HueLightBulb> getLights() {
		return lights.values();
	}

	public HueLightBulb getLight(int id) {
		return lights.get(id);
	}

	public Set<Integer> getLightIds() {
		return lights.keySet();
	}

	public boolean add(HueLightBulb light) {
		if(light.bridge!=bridge) {
			throw new IllegalArgumentException("A group can only contain lights from the same bridge");
		}
		if(id==0) {
			return false;
		}
		throw new UnsupportedOperationException("This is not supported by version 1.0 of the Philips Hue API");
	}

	public boolean remove(HueLightBulb light) {
		if(light.bridge!=bridge) {
			throw new IllegalArgumentException("A group may only contain lights from the same bridge");
		}
		if(id==0) {
			throw new IllegalArgumentException("It is not allowed to remove a light from the implicit group");
		}
		throw new UnsupportedOperationException("This is not supported by version 1.0 of the Philips Hue API");
	}

	@Override
	public void setOn(boolean on) {
		stateChange("on", on);
		for(HueLightBulb light : getLights()) {
			light.on = on;
		}
	}

	@Override
	public void setBrightness(int brightness) {
		if(brightness<0 || brightness>255) {
			throw new IllegalArgumentException("Brightness must be between 0-255");
		}
		stateChange("bri", brightness);
		for(HueLightBulb light : getLights()) {
			light.brightness = brightness;
		}
	}

	@Override
	public void setHue(int hue) {
		if(hue<0 || hue>65535) {
			throw new IllegalArgumentException("Hue must be between 0-65535");
		}
		stateChange("hue", hue);
		for(HueLightBulb light : getLights()) {
			light.hue = hue;
		}
	}

	@Override
	public void setSaturation(int saturation) {
		if(saturation<0 || saturation>255) {
			throw new IllegalArgumentException("Saturation must be between 0-255");
		}
		stateChange("sat", saturation);
		for(HueLightBulb light : getLights()) {
			light.saturation = saturation;
		}
	}

	@Override
	public void setCieXY(double ciex, double ciey) {
		if(ciex<0 || ciex>1 || ciey<0 || ciey>1) {
			throw new IllegalArgumentException("A cie coordinate must be between 0.0-1.0");
		}
		stateChange("xy", new JSONArray(Arrays.asList((float)ciex,(float)ciey)));
		for(HueLightBulb light : getLights()) {
			light.ciex = ciex;
			light.ciey = ciey;
		}
	}

	@Override
	public void setColorTemperature(int colorTemperature) {
		if(colorTemperature<153 || colorTemperature>500) {
			throw new IllegalArgumentException("ColorTemperature must be between 153-500");
		}
		stateChange("ct", colorTemperature);
		for(HueLightBulb light : getLights()) {
			light.colorTemperature = colorTemperature;
		}
	}

	@Override
	public void setEffect(Effect effect) {
		stateChange("effect", effect.name);
		for(HueLightBulb light : getLights()) {
			light.effect = effect;
		}
	}

	@Override
	public void setAlert(Alert alert) {
		stateChange("alert", alert.name);
	}

	@Override
	public String toString() {
		String ids = "";
		for(Integer lightId : getLightIds()) {
			if(ids.length()>0) {
				ids += ",";
			}
			ids += lightId;
		}

		return getId() +"(" +getName() +")" +"["
			   +ids
			   +"]";
	}

	@Override
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
		return bridge.checkedSuccessRequest(PUT, "/lights/" + getId() + "/state", json);
	}

	private List<JSONObject> stateChange(String param, Object value) {
		final JSONObject stateTransaction = stateTransactionJson.get();
		if(stateTransaction==null) {
			JSONWriter json = JO();
			if(transitionTime!=null) {
				json = json.key("transitiontime").value(transitionTime);
			}
			return bridge.checkedSuccessRequest(PUT, "/groups/" + getId() + "/action", json.key(param).value(value));
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
