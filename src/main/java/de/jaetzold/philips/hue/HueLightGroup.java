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
 * This class represents a group of lights that is defined on the hue bridge device. By convention, the group with the id '0' always
 * exists and contains all lights connected to the bridge device.
 * <p>
 *     An instance of this class is not created directly. Instead query a {@link HueBridge} for its groups using either
 *     {@link HueBridge#getGroups()}or {@link HueBridge#getGroup(Integer)}.
 * </p>
 *
 * Note: Group creation and deletion is not supported as of Philips Hue API version 1.0.
 * So effectively only the implicit group 0 containing all the lights can be used. But see {@link HueVirtualLightGroup} for a way around that.
 *
 * <p>See <a href="http://developers.meethue.com/2_groupsapi.html">Philips hue API, Section 2</a> for further reference.</p>
 *
 * @author Stephan Jaetzold <p><small>Created at 22.03.13, 14:10</small>
 */
public class HueLightGroup implements HueLight {
	final Integer id;
	final HueBridge bridge;

	String name;
	final Map<Integer, HueLightBulb> lights;

	Integer transitionTime;

	/**
	 * This constructor is package private, since groups are (at least for the moment) not to be created. A {@link HueBridge} is queried for them.
	 */
	HueLightGroup(HueBridge bridge, Integer id) {
		this(bridge, id, null);
	}

	/**
	 * This constructor is package private, since groups are (at least for the moment) not to be created. A {@link HueBridge} is queried for them.
	 */
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
		final JSONObject response = bridge.checkedSuccessRequest(PUT, "/lights/" +id, JO().key("name").value(name.trim())).get(0);
		final String actualName = response.getJSONObject("success").optString("/lights/" + id + "/name");
		this.name = actualName!=null ? actualName : name;
	}

	/**
	 * Provide a collection of all light bulbs that are currently in this group.
	 *
	 * @see #getLight(Integer)
	 * @see #getLightIds()
	 *
	 * @return A collection of all light bulbs that are currently in this group.
	 */
	public Collection<? extends HueLightBulb> getLights() {
		return lights.values();
	}

	/**
	 * Get a light bulb with a known id. Either saved from earlier or from the ids returned by {@link #getLightIds()}.
	 *
	 * @param id the id of the light bulb to return
	 *
	 * @return A {@link HueLightBulb} with the given id or null if none with that id is currently in this group.
	 */
	public HueLightBulb getLight(Integer id) {
		return lights.get(id);
	}

	/**
	 * The ids of all the light bulbs currently in this group.
	 *
	 * @return The ids of all the light bulbs currently in this group.
	 */
	public Set<Integer> getLightIds() {
		return lights.keySet();
	}

	/**
	 * Add a light bulb to this group. The light must belong to the same bridge as this group.
	 *
	 * <p>Note: This is not supported by version 1.0 of the Philips Hue API and you will get an exception if an unsupported modification is attempted.</p>
	 *
	 * @param light the light to add to this group
	 * @return true, if the group was modified as a result of this call, false otherwise.
	 */
	@SuppressWarnings("UnusedDeclaration")
	public boolean add(HueLightBulb light) {
		if(light.getBridge()!=getBridge()) {
			throw new IllegalArgumentException("A group can only contain lights from the same bridge");
		}
		if(id==0) {
			return false;
		}
		throw new UnsupportedOperationException("This is not supported by version 1.0 of the Philips Hue API");
	}

	/**
	 * Remove a light bulb from this group. The light must belong to the same bridge as this group, but is not required to actually be in this group,
	 * the group will just not be modified then.
	 *
	 * <p>Note: This is not supported by version 1.0 of the Philips Hue API and you will get an exception if an unsupported modification is attempted.</p>
	 *
	 * @param light the light to remove from this group
	 * @return true, if the group was modified as a result of this call, false otherwise.
	 */
	@SuppressWarnings("UnusedDeclaration")
	public boolean remove(HueLightBulb light) {
		if(light.getBridge()!=getBridge()) {
			throw new IllegalArgumentException("A group may only contain lights from the same bridge");
		}
		if(id==0) {
			throw new IllegalArgumentException("It is not allowed to remove a light from the implicit group");
		}
		throw new UnsupportedOperationException("This is not supported by version 1.0 of the Philips Hue API");
	}

	@Override
	public void setOn(Boolean on) {
		stateChange("on", on);
		for(HueLightBulb light : getLights()) {
			light.on = on;
		}
	}

	@Override
	public void setBrightness(Integer brightness) {
		if(brightness<0 || brightness>255) {
			throw new IllegalArgumentException("Brightness must be between 0-255");
		}
		stateChange("bri", brightness);
		for(HueLightBulb light : getLights()) {
			light.brightness = brightness;
		}
	}

	@Override
	public void setHue(Integer hue) {
		if(hue<0 || hue>65535) {
			throw new IllegalArgumentException("Hue must be between 0-65535");
		}
		stateChange("hue", hue);
		for(HueLightBulb light : getLights()) {
			light.hue = hue;
		}
	}

	@Override
	public void setSaturation(Integer saturation) {
		if(saturation<0 || saturation>255) {
			throw new IllegalArgumentException("Saturation must be between 0-255");
		}
		stateChange("sat", saturation);
		for(HueLightBulb light : getLights()) {
			light.saturation = saturation;
		}
	}

	@Override
	public void setCieXY(Double ciex, Double ciey) {
		if(ciex<0 || ciex>1 || ciey<0 || ciey>1) {
			throw new IllegalArgumentException("A cie coordinate must be between 0.0-1.0");
		}
		stateChange("xy", new JSONArray(Arrays.asList(ciex.floatValue(),ciey.floatValue())));
		for(HueLightBulb light : getLights()) {
			light.ciex = ciex;
			light.ciey = ciey;
		}
	}

	@Override
	public void setColorTemperature(Integer colorTemperature) {
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
			return bridge.checkedSuccessRequest(PUT, "/groups/" + getId() + "/state", json);
		} else {
			return null;
		}
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
