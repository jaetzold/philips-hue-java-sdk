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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * A group of lights that is entirely client-side.
 * The bridge is only there as the "maintainer" of the virtual group. The actual lights can be from multiple bridges.
 * They even can be other groups.
 * <p>
 *     A virtual group is associated with a bridge device, but this is solely to have a place to store and query virtual groups at runtime
 *     the same way that regular groups are stored and queried.
 * </p>
 * <p>
 *     Note: Since a virtual group lives client side it is not saved on the bridge device. If you want your virtual group to be persistent
 *     you have to implement that on your own.
 * </p>
 *
 * @author Stephan Jaetzold <p><small>Created at 22.03.13, 15:16</small>
 */
public class HueVirtualLightGroup implements HueLight {
	final Integer id;
	final HueBridge bridge;

	String name;
	final Set<HueLight> lights;

	Integer transitionTime;

	/**
	 * Create a virtual group that is associated with the given bridge.
	 * The group will initially contain no lights.
	 *
	 * @param bridge the bridge to store this virtual group at.
	 * @param id a unique id for this group. It must be unique among all virtual groups associated with the given bridge.
	 * @param name A name for this virtual group.
	 */
	@SuppressWarnings("UnusedDeclaration")
	public HueVirtualLightGroup(HueBridge bridge, Integer id, String name) {
		this(bridge, id, name, new HueLight[0]);
	}

	/**
	 * Create a virtual group that is associated with the given bridge.
	 * The group will initially contain all the given lights. Note: These lights need not to belong to the same bridge.
	 *
	 * @param bridge the bridge to store this virtual group at.
	 * @param id a unique id for this group. It must be unique among all virtual groups associated with the given bridge.
	 * @param name A name for this virtual group.
	 * @param lights The lights this virtual group should contain initially.
	 */
	public HueVirtualLightGroup(HueBridge bridge, Integer id, String name, HueLight... lights) {
		if(id==null || id<0) {
			throw new IllegalArgumentException("id has to be non-negative and non-null");
		}
		if(bridge==null) {
			throw new IllegalArgumentException("bridge may not be null");
		}
		this.bridge = bridge;
		this.id = id;
		this.name = name;
		this.lights = new LinkedHashSet<>();
		if(this.bridge.getVirtualGroupIds().contains(getId())) {
			throw new IllegalArgumentException("There is already a virtual group with id " +getId() +" on " +getBridge());
		} else {
			this.bridge.virtualGroups.put(getId(), this);
		}
		Collections.addAll(this.lights, lights);
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
		if(name==null) {
			name  = "";
		}
		this.name = name;
	}

	/**
	 * Provide a collection of all lights that are currently in this virtual group.
	 *
	 * @see #getLight(Integer)
	 * @see #getLights(Integer)
	 * @see #getLightIds()
	 *
	 * @return A collection of all lights that are currently in this virtual group.
	 */
	public Collection<? extends HueLight> getLights() {
		return lights;
	}

	/**
	 * Get a light with a known id. Either saved from earlier or from the ids returned by {@link #getLightIds()}.
	 * Note: This is ambiguous since a virtual group may contain different light types from different bridges. So there may be more than
	 * one light with a given id in this virtual group. The returned light may be any one of them.
	 *
	 * @param id the id of the light to return
	 *
	 * @return A {@link HueLight} with the given id or null if none with that id is currently in this group.
	 */
	public HueLight getLight(Integer id) {
		for(HueLight light : lights) {
			if(light.getId().equals(id)) {
				return light;
			}
		}
		return null;
	}

	/**
	 * Get all lights in this virtual group that have a specific id.
	 * Note: This is not a single light, but a whole collection since a virtual group may contain different light types from different bridges.
	 * So there may be more than one light with a given id in this virtual group.
	 *
	 * @param id the id of the light to return
	 *
	 * @return A collection of {@link HueLight} that all have the given id. The collection will be empty if none with that id are currently in this virtual group.
	 */
	public Collection<? extends HueLight> getLights(Integer id) {
		final ArrayList<HueLight> result = new ArrayList<>();
		for(HueLight light : lights) {
			if(light.getId().equals(id)) {
				result.add(light);
			}
		}
		return result;
	}

	/**
	 * The ids of all the lights currently in this virtual group.
	 * Note: The number of lights in this virtual group may be higher than the size of the returned set since there may be more
	 * than one light with the same id, but from a different type or a different bridge.
	 *
	 * @return All ids for which at least one light currently is in this virtual group.
	 */
	public Set<Integer> getLightIds() {
		Set<Integer> result = new TreeSet<>();
		for(HueLight light : lights) {
			result.add(light.getId());
		}
		return result;
	}

	/**
	 * Add a light to this virtual group.
	 *
	 * <p>Note: This light may not result in a reference cycle or an {@link IllegalArgumentException} will be thrown.</p>
	 *
	 * @param light the light to add to this virtual group
	 * @return true, if the group was modified as a result of this call, false otherwise.
	 */
	public boolean add(HueLight light) {
		// check for cycles first
		if(light==this) {
			throw new IllegalArgumentException("Can not add me to myself.");
		}
		ensureNoBackReference(light);

		return lights.add(light);
	}

	/**
	 * Remove a light from this virtual group.
	 *
	 * @param light the light to remove from this virtual group
	 * @return true, if the group was modified as a result of this call, false otherwise.
	 */
	@SuppressWarnings("UnusedDeclaration")
	public boolean remove(HueLight light) {
		return lights.remove(light);
	}

	@Override
	public void setOn(Boolean on) {
		for(HueLight light : getLights()) {
			light.setOn(on);
		}
	}

	@Override
	public void setBrightness(Integer brightness) {
		if(brightness<0 || brightness>255) {
			throw new IllegalArgumentException("Brightness must be between 0-255");
		}
		for(HueLight light : getLights()) {
			light.setBrightness(brightness);
		}
	}

	@Override
	public void setHue(Integer hue) {
		if(hue<0 || hue>65535) {
			throw new IllegalArgumentException("Hue must be between 0-65535");
		}
		for(HueLight light : getLights()) {
			light.setHue(hue);
		}
	}

	@Override
	public void setSaturation(Integer saturation) {
		if(saturation<0 || saturation>255) {
			throw new IllegalArgumentException("Saturation must be between 0-255");
		}
		for(HueLight light : getLights()) {
			light.setSaturation(saturation);
		}
	}

	@Override
	public void setCieXY(Double ciex, Double ciey) {
		if(ciex<0 || ciex>1 || ciey<0 || ciey>1) {
			throw new IllegalArgumentException("A cie coordinate must be between 0.0-1.0");
		}
		for(HueLight light : getLights()) {
			light.setCieXY(ciex, ciey);
		}
	}

	@Override
	public void setColorTemperature(Integer colorTemperature) {
		if(colorTemperature<153 || colorTemperature>500) {
			throw new IllegalArgumentException("ColorTemperature must be between 153-500");
		}
		for(HueLight light : getLights()) {
			light.setColorTemperature(colorTemperature);
		}
	}

	@Override
	public void setEffect(Effect effect) {
		for(HueLight light : getLights()) {
			light.setEffect(effect);
		}
	}

	@Override
	public void setAlert(Alert alert) {
		for(HueLight light : getLights()) {
			light.setAlert(alert);
		}
	}

	@Override
	public String toString() {
		String children = "";
		for(HueLight light : getLights()) {
			if(children.length()>0) {
				children += ",";
			}
			children += light.toString();
		}

		return getId() +"(" +getName() +")" +"["
			   +children
			   +"]";
	}

	@Override
	public void stateChangeTransaction(Integer transitionTime, Runnable changes) {
		final Set<HueLight> lightsForTransaction = new LinkedHashSet<>();
		// collect the actual LightBulbs or Bridge Groups here to avoid opening a transaction twice on them because a light is contained in two virtual groups
		collectReal(lightsForTransaction, this);
		// reverse them to get the same order as when changing without transaction. The order is reversed because the last opened transaction gets commited first
		final ArrayList<HueLight> reversedList = new ArrayList<>(lightsForTransaction);
		Collections.reverse(reversedList);
		stateChangeTransactionOn(reversedList, transitionTime, changes);
	}

	// *****************************************
	// Implementation internal methods
	// *****************************************

	private void ensureNoBackReference(HueLight light) {
		if(light instanceof HueVirtualLightGroup) {
			HueVirtualLightGroup group = (HueVirtualLightGroup)light;
			for(HueLight light2 : group.getLights()) {
				if(light2==this) {
					throw new IllegalArgumentException("Adding the given light would result in a circular reference because " +light +" references " +this);
				}
				ensureNoBackReference(light2);
			}
		}
	}

	private void collectReal(Set<HueLight> lightsForTransaction, HueLight light) {
		if(light instanceof HueVirtualLightGroup) {
			HueVirtualLightGroup virtual = (HueVirtualLightGroup)light;
			for(HueLight light2 : virtual.getLights()) {
				collectReal(lightsForTransaction, light2);
			}
		} else {
			lightsForTransaction.add(light);
		}
	}

	private void stateChangeTransactionOn(final Collection<? extends HueLight> lightsForTransaction, final Integer transitionTime, final Runnable changes) {
		final Iterator<? extends HueLight> iterator = lightsForTransaction.iterator();
		if(!iterator.hasNext()) {
			changes.run();
			return;
		}
		final HueLight currentLight = iterator.next();
		iterator.remove();
		currentLight.stateChangeTransaction(transitionTime, new Runnable() {
			@Override
			public void run() {
				stateChangeTransactionOn(lightsForTransaction, transitionTime, changes);
			}
		});
	}
}
