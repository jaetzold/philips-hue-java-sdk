package de.jaetzold.philips.hue;

/** @author Stephan Jaetzold <p><small>Created at 22.03.13, 14:05</small> */
public interface HueLight {
	int HUE_RED = 0;
	int HUE_RED_2 = 65535;
	int HUE_GREEN = 25500;
	int HUE_BLUE = 46920;

	public Integer getId();

	public HueBridge getBridge();

	public Integer getTransitionTime();

	public void setTransitionTime(Integer transitionTime);

	public String getName();

	public void setName(String name);

	public void setOn(boolean on);

	public void setBrightness(int brightness);

	public void setHue(int hue);

	public void setSaturation(int saturation);

	public void setCieXY(double ciex, double ciey);

	public void setColorTemperature(int colorTemperature);

	public void setEffect(Effect effect);

	public void setAlert(Alert alert);

	// Beware: This only changes the state of _this_ light in bulk. Any other light changed by the runnable is changed directly
	// If more lights should be changed in one transaction it is possible by nesting runnables that call stateChangeTransaction on other lights.
	// transactions on the same light can not be nested. This will result in an exception.
	// Do not expect transactions to always completely rollback. Especially with nested transactions involved it is possible that
	// an inner transaction is already committed if an outer transaction fails. And the bridge may only have success in setting part of the state
	// that got committed. But we're dealing with light here and not financial data, so i guess that's probably o.k.
	void stateChangeTransaction(Integer transitionTime, Runnable changes);

	public static enum ColorMode {
		HS,CT,XY
	}

	public static enum Alert {
		NONE("none"), SELECT("select"), LSELECT("select");
		public final String name;

		private Alert(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public static enum Effect {
		NONE("none"), COLORLOOP("colorloop");
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
