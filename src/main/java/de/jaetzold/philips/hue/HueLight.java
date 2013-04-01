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

/**
 * A control interface for light devices.
 * The main purpose it serves is to be able to handle single light bulbs and whole groups of them in an interchangeable fashion.
 * But since being in a group does not prevent lights from being controlled independently as well many state querying methods do not apply
 * to groups since it is not defined whether a group is 'on' if not all the lights in the group have the same state.
 *
 * @author Stephan Jaetzold <p><small>Created at 22.03.13, 14:05</small>
 */
public interface HueLight {
	/**
	 * The low hue value that results in red.
	 * @see #setHue(Integer)
	 */
	public static final int HUE_RED = 0;
	/**
	 * The high hue value that results in red.
	 * @see #setHue(Integer)
	 */
	public static final int HUE_RED_2 = 65535;
	/**
	 * The hue value that results in green.
	 * @see #setHue(Integer)
	 */
	public static final int HUE_GREEN = 25500;
	/**
	 * The hue value that results in blue.
	 * @see #setHue(Integer)
	 */
	public static final int HUE_BLUE = 46920;

	/**
	 * A (per bridge device) unique id of this light.
	 *
	 * @return The id of this light.
	 */
	public Integer getId();

	/**
	 * The bridge instance this light is associated with.
	 * @return The bridge instance this light is associated with.
	 */
	public HueBridge getBridge();

	/**
	 * The transition time that will be used for state changes in units of 100 milliseconds.
	 * @return The transition time that will be used for state changes.
	 */
	@SuppressWarnings("UnusedDeclaration")
	public Integer getTransitionTime();

	/**
	 * Set the transition time that will be used for state changes in units of 100 milliseconds.
	 * Set it to null and the bridges default will be used.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a>
	 * and <a href="http://developers.meethue.com/2_groupsapi.html#25_set_group_state">Section 2.5</a>
	 * for further reference.</p>
	 *
	 * @param transitionTime The transition time to use for subsequent state changes or null if the bridges default should be used.
	 */
	public void setTransitionTime(Integer transitionTime);

	/**
	 * A name to (not necessarily uniquely) identify the light.
	 *
	 * @return The name of this light.
	 */
	public String getName();

	/**
	 * Assign a new name to the light. For lights completely represented on the bridge device this name is actually sent to and stored on the device.
	 * It then must be between 0 and 32 characters long.
	 * For a {@link HueVirtualLightGroup} this name is not stored on the device and may be of any length.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#15_set_light_attributes_rename">Philips hue API, Section 1.5</a>
	 * and <a href="http://developers.meethue.com/2_groupsapi.html#24_set_group_attributes">Section 2.4</a>
	 * for further reference.</p>
	 *
	 *  @param name The new name of this light.
	 */
	@SuppressWarnings("UnusedDeclaration")
	public void setName(String name);

	/**
	 * Switch this light 'on' or 'off'.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a>
	 * and <a href="http://developers.meethue.com/2_groupsapi.html#25_set_group_state">Section 2.5</a>
	 * for further reference.</p>
	 *
	 * @param on true, if the light should be on, false if it should be off.
	 */
	public void setOn(Boolean on);

	/**
	 * Set the brightness of this light to the given value.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a>
	 * and <a href="http://developers.meethue.com/2_groupsapi.html#25_set_group_state">Section 2.5</a>
	 * for further reference.</p>
	 *
	 * @param brightness a brightness value between 0 (lowest that is not off) and 255 (highest)
	 */
	public void setBrightness(Integer brightness);

	/**
	 * Set the hue of this light to the given value.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a>
	 * and <a href="http://developers.meethue.com/2_groupsapi.html#25_set_group_state">Section 2.5</a>
	 * for further reference.</p>
	 *
	 * @see #HUE_RED
	 * @see #HUE_GREEN
	 * @see #HUE_BLUE
	 *
	 * @param hue a hue value between 0 and 65535 (which are both red)
	 */
	public void setHue(Integer hue);

	/**
	 * Set the saturation of this light to the given value.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a>
	 * and <a href="http://developers.meethue.com/2_groupsapi.html#25_set_group_state">Section 2.5</a>
	 * for further reference.</p>
	 *
	 * @param saturation a saturation value between 0 (white) and 255 (colored)
	 */
	public void setSaturation(Integer saturation);

	/**
	 * Set the x and y coordinates of a color in CIE color space.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a>
	 * and <a href="http://developers.meethue.com/2_groupsapi.html#25_set_group_state">Section 2.5</a>
	 * for further reference.</p>
	 *
	 * @param ciex the x coordinate in CIE color space between 0 and 1
	 * @param ciey the y coordinate in CIE color space between 0 and 1
	 */
	public void setCieXY(Double ciex, Double ciey);

	/**
	 * Set the mired color temperature of this light to the given value.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a>
	 * and <a href="http://developers.meethue.com/2_groupsapi.html#25_set_group_state">Section 2.5</a>
	 * for further reference.</p>
	 *
	 * @param colorTemperature a color temperature value in mired between 153 (6500K) and 500 (2000K)
	 */
	public void setColorTemperature(Integer colorTemperature);

	/**
	 * Set the effect state of this light to the given value.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a>
	 * and <a href="http://developers.meethue.com/2_groupsapi.html#25_set_group_state">Section 2.5</a>
	 * for further reference.</p>
	 *
	 * @param effect the effect. May not be null, use {@link Effect#NONE} if no effect should be set.
	 */
	public void setEffect(Effect effect);

	/**
	 * Set the alert state of this light to the given value.
	 *
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a>
	 * and <a href="http://developers.meethue.com/2_groupsapi.html#25_set_group_state">Section 2.5</a>
	 * for further reference.</p>
	 *
	 * @param alert the alert. May not be null, use {@link Alert#NONE} if no alert should be set.
	 */
	public void setAlert(Alert alert);

	/**
	 * Change multiple states of this light at once, with a single call to the bridge device per light/group.
	 * Use the given transition time (in units of 100 milliseconds) for the state change.
	 * <p>
	 * Beware: This only changes the state of <em>this</em> light in bulk. Any other light changed by the runnable is changed directly.
	 * If more lights should be changed in one transaction it is possible by nesting runnables that call stateChangeTransaction on other lights.
	 * Transactions on the same light can not be nested. This will result in an exception, regardless of who opened the transaction.
	 * Note: A virtual light groups transaction opens a transaction on all its lights.
	 * </P><p>
	 * Beware: Do not expect transactions to always completely rollback. Especially with nested transactions involved it is possible that
	 * an inner transaction is already committed if an outer transaction fails. And the bridge may only have success in setting part of the state
	 * that got committed. But we're dealing with light here and not financial data, so i guess that's probably o.k.
	 * </p>
	 *
	 * @param transitionTime The transition time (in units of 100 milliseconds) for the state change.
	 * @param changes The code that applied the state changes.
	 */
	public void stateChangeTransaction(Integer transitionTime, Runnable changes);

	/**
	 * Enumerate the different modes that can be used to set the color of a light.
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#14_get_light_attributes_and_state">Philips hue API, Section 1.4</a> for further reference.</p>
	 */
	public static enum ColorMode {
		HS,CT,XY
	}

	/**
	 * Enumerate the different alert effects.
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a> for further reference.</p>
	 */
	public static enum Alert {
		/**
		 * The light is performing no alert effect.
		 */
		NONE("none"),
		/**
		 * The light is performing one <a href="http://developers.meethue.com/6_glossaryterms.html">breathe cycle</a>.
		 */
		@SuppressWarnings("UnusedDeclaration")
		SELECT("select"),
		/**
		 * The light is performing <a href="http://developers.meethue.com/6_glossaryterms.html">breathe cycles</a> for 30 seconds or until the alert is set to {@link #NONE}.
		 */
		LSELECT("lselect");
		/**
		 * The api internal name this alert goes by.
		 */
		public final String name;

		private Alert(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	/**
	 * Enumerate the different light effects.
	 * <p>See <a href="http://developers.meethue.com/1_lightsapi.html#16_set_light_state">Philips hue API, Section 1.6</a> for further reference.</p>
	 */
	public static enum Effect {
		/**
		 * The light is performing no effect.
		 */
		NONE("none"),
		/**
		 * The light will cycle through all hues using the current brightness and saturation settings.
		 */
		COLORLOOP("colorloop");
		/**
		 * The api internal name this effect goes by.
		 */
		public final String name;

		private Effect(String name) {
			this.name = name;
		}

		public static Effect fromName(String name) {
			for(Effect effect : Effect.values()) {
				if(effect.name.equals(name)) {
					return effect;
				}
			}
			return null;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
