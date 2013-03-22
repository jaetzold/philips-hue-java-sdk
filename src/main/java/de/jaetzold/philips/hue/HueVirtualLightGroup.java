package de.jaetzold.philips.hue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * A group of lights that is entirely client-side.
 * The bridge is only there as the "maintainer" of the virtual group. The actual lights can be from multiple bridges. And they can be other groups.
 *
 * @author Stephan Jaetzold <p><small>Created at 22.03.13, 15:16</small>
 */
public class HueVirtualLightGroup implements HueLight {
	final Integer id;
	final HueBridge bridge;

	String name;
	final Set<HueLight> lights;

	Integer transitionTime;

	public HueVirtualLightGroup(HueBridge bridge, Integer id, String name) {
		this(bridge, id, name, new HueLight[0]);
	}

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

	public Collection<? extends HueLight> getLights() {
		return lights;
	}

	public HueLight getLight(int id) {
		for(HueLight light : lights) {
			if(light.getId().equals(id)) {
				return light;
			}
		}
		return null;
	}

	public Collection<? extends HueLight> getLights(int id) {
		final ArrayList<HueLight> result = new ArrayList<>();
		for(HueLight light : lights) {
			if(light.getId().equals(id)) {
				result.add(light);
			}
		}
		return result;
	}

	public Set<Integer> getLightIds() {
		Set<Integer> result = new TreeSet<>();
		for(HueLight light : lights) {
			result.add(light.getId());
		}
		return result;
	}

	public boolean add(HueLight light) {
		// check for cycles first
		if(light==this) {
			throw new IllegalArgumentException("Can not add me to myself.");
		}
		ensureNoBackReference(light);

		return lights.add(light);
	}

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

	public boolean remove(HueLight light) {
		return lights.remove(light);
	}

	@Override
	public void setOn(boolean on) {
		for(HueLight light : getLights()) {
			light.setOn(on);
		}
	}

	@Override
	public void setBrightness(int brightness) {
		if(brightness<0 || brightness>255) {
			throw new IllegalArgumentException("Brightness must be between 0-255");
		}
		for(HueLight light : getLights()) {
			light.setBrightness(brightness);
		}
	}

	@Override
	public void setHue(int hue) {
		if(hue<0 || hue>65535) {
			throw new IllegalArgumentException("Hue must be between 0-65535");
		}
		for(HueLight light : getLights()) {
			light.setHue(hue);
		}
	}

	@Override
	public void setSaturation(int saturation) {
		if(saturation<0 || saturation>255) {
			throw new IllegalArgumentException("Saturation must be between 0-255");
		}
		for(HueLight light : getLights()) {
			light.setSaturation(saturation);
		}
	}

	@Override
	public void setCieXY(double ciex, double ciey) {
		if(ciex<0 || ciex>1 || ciey<0 || ciey>1) {
			throw new IllegalArgumentException("A cie coordinate must be between 0.0-1.0");
		}
		for(HueLight light : getLights()) {
			light.setCieXY(ciex, ciey);
		}
	}

	@Override
	public void setColorTemperature(int colorTemperature) {
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
