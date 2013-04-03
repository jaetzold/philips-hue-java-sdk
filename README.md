An SDK for accessing a Philips Hue installation with Java.
==========================================================

The aim is a simple and clean interface and no dependencies aside from Java 7 SE.
But since the API of the Hue is JSON based, code from the following project has been borrowed:
https://github.com/douglascrockford/JSON-java
( actual commit used: b883a848a6af94db57e1b8223ab9b6d96707a9e7 )

The SDK supports the lights and groups API completely and even supports groups across multiple bridges.
It is currently missing support for the schedules API. From the configuration API only the user and name configuration is implemented
since i currently see no use for the other parts. But it would be very easy to add that.

From what i can tell so far it works well. But some more "peripheral" methods are not really tested. For example the search for new lights.
I would need light bulbs unknown to my bridge to do that.

Usage
-----
<br/>
***Find philips hue bridge devices on the local network:***

    List<HueBridge> bridges = HueBridge.discover();

***Create a new access token (username) on a bridge:***

    bridge.authenticate(true);
    // press button on the brige
    String username = bridge.getUsername(); // <- save that somewhere for next time

***Use an existing username on a bridge:***

    bridge.authenticate(username, true);

***Get lights***

    Collection<? extends HueLightBulb> lights = bridge.getLights();
    // or, if an id is already known:
    HueLightBulb light = bridge.getLight(1);

***Change light state***

    light.setOn(true);
    light.setBrightness(240);

***Change light state in a single transaction (a single request to the bridge)***

    light.stateChangeTransaction(10, new Runnable() {
        @Override
        void run() {
            light.hue = HueLightBulb.HUE_RED
            light.saturation = 200
            light.brightness = 100
        }
    })

***Use groups of lights to change state of multiple lights at once***

    HueLightGroup group = bridge.getGroup(0);
    group.setOn(false);

***Use virtual groups of lights to be more flexible, e.g. group lights and groups from different bridges***

    List<HueBridge> bridges = HueBridge.discover();
    HueBridge bridge1 = bridges.get(0);
    HueBridge bridge2 = bridges.get(1);
    HueLight lightA = bridge1.getLight(1);
    HueLight lightB = bridge2.getGroup(0);
    HueVirtualLightGroup virtualGroup = new HueVirtualLightGroup(bridge1, 1, "Cross bridge group", lightA, lightB)
    virtualGroup.setOn(true);


For more examples (in groovy, but understandable for every Java programmer, i'd think) look at [UsageExamples.groovy](https://github.com/jaetzold/philips-hue-java-sdk/blob/master/src/test/groovy/UsageExamples.groovy).
