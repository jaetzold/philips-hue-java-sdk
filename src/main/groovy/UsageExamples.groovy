import de.jaetzold.philips.hue.HueHub
import de.jaetzold.philips.hue.HueLightBulb

/**
 Still to to:
 * light alert
 * light effect

 * group as a variant of light (has the same properties and capabilities after all)
 * Will not create new groups on hub until delete is possible (Groups are limited to 16 and i don't want to be responsible for this being full)
 * ? SDK-Internal group implementation that actually syncs state of lights without a group on the hub

 * auto-update internal state in intervals to sync with external changes. Maybe only on state access after interval.
 * ? Events on (even external) state changes. External will only be enabled when there is a listener to preserve resources.

 * discover of new lights. Maybe only on access after interval.
 * auto-update internal state of hub. Maybe only on access after interval.
 * ? Events on lights changes/hub state changes. External will only be enabled when there is a listener to preserve resources.

 Future
 * Group creation/deletion when Bridge API supports deletion.
 * Schedules support.
 * Extended configuration support (List/delete Users)

 * @author Stephan Jaetzold
 * <p><small>Created at 15.03.13, 16:23</small>
 */

List<HueHub> hubs = discoverAndAuthenticate()

if(hubs.isEmpty()) {
    println("No hub found.")
    System.exit(1)
}
HueHub hub = hubs.get(0)

listLights(hub)

hub.setName("Jaetzold's Hue")
println("After changing name: " +hub)

if(hubs.lights.isEmpty()) {
    println("No lights found.")
    System.exit(2)
}
HueLightBulb light = hub.getLight(hub.lightIds[0])

light.transitionTime = 1    // this is used in all subsequent (non-transactional) state changes. 'null' uses the bridges default
light.on = true;
blinkOnce(light, 1000)

sweepBrightness(light, 1000)
sweepHueSaturation(light, 1000)
sweepCieXY(light, 1000)
sweepColorTemperature(light, 1000)

multipleStateChangeInOneRequest(light, 4000)

alert(light, 10000)
effect(light, 10000)

def effect(HueLightBulb light, int waitMillis) {
    println("effect '$light.name'")
    light.effect = HueLightBulb.Effect.COLORLOOP
    Thread.sleep(waitMillis)
    light.effect = HueLightBulb.Effect.NONE
}

def alert(HueLightBulb light, int waitMillis) {
    println("alert '$light.name'")
    light.alert = HueLightBulb.Alert.LSELECT
    Thread.sleep(waitMillis)
    light.alert = HueLightBulb.Alert.NONE
}

def multipleStateChangeInOneRequest(HueLightBulb light, int transisitonMillis) {
    println("multipleStateChangeInOneRequest '$light.name'")
    light.stateChangeTransaction(0, new Runnable() {
        @Override
        void run() {
            light.hue = HueLightBulb.HUE_RED
            light.saturation = 0
            light.brightness = 100
        }
    })
    // or a bit groovier using closures
    light.stateChangeTransaction((int)Math.round(transisitonMillis/100.0)) {
        light.hue = HueLightBulb.HUE_GREEN  // Will be ignored because overwritten later in the transaction
        light.saturation = 255
        light.brightness = 255
        light.hue = HueLightBulb.HUE_BLUE
    }
    Thread.sleep(transisitonMillis)
}

def sweepColorTemperature(HueLightBulb light, int waitMillis) {
    print("sweepColorTemperature '$light.name': ")
    light.colorTemperature = 500
    print("500 ")
    Thread.sleep(waitMillis)
    light.colorTemperature = 450
    print("450 ")
    Thread.sleep(waitMillis)
    light.colorTemperature = 400
    print("400 ")
    Thread.sleep(waitMillis)
    light.colorTemperature = 350
    print("350 ")
    Thread.sleep(waitMillis)
    light.colorTemperature = 300
    print("300 ")
    Thread.sleep(waitMillis)
    light.colorTemperature = 250
    print("250 ")
    Thread.sleep(waitMillis)
    light.colorTemperature = 200
    print("200 ")
    Thread.sleep(waitMillis)
    light.colorTemperature = 153
    print("153 ")
    Thread.sleep(waitMillis)
    println()
}

def sweepCieXY(HueLightBulb light, int waitMillis) {
    print("sweepCieXY '$light.name': ")
    light.setCieXY(0, 0)
    print("0,0 ")
    Thread.sleep(waitMillis)
    light.setCieXY(0.5, 0)
    print("0.5,0 ")
    Thread.sleep(waitMillis)
    light.setCieXY(1, 0.5)
    print("1,0.5 ")
    Thread.sleep(waitMillis)
    light.setCieXY(1, 1)
    print("1,1 ")
    Thread.sleep(waitMillis)
    light.setCieXY(0.5, 1)
    print("0.5,1 ")
    Thread.sleep(waitMillis)
    light.setCieXY(0, 1)
    print("0,1 ")
    Thread.sleep(waitMillis)
    light.setCieXY(0, 0.5)
    print("0,0.5 ")
    Thread.sleep(waitMillis)
    light.setCieXY(0.5, 0.5)
    print("0.5,0.5 ")
    Thread.sleep(waitMillis)
    println()
}

def sweepHueSaturation(HueLightBulb light, int waitMillis) {
    print("sweepHueSaturation '$light.name': ")
    light.hue = HueLightBulb.HUE_RED
    light.saturation = 255
    print("RED saturated")
    Thread.sleep(waitMillis)
    light.saturation = 127
    Thread.sleep(waitMillis)
    light.saturation = 0
    print("-not saturated ")
    Thread.sleep(waitMillis)
    light.hue = HueLightBulb.HUE_GREEN
    print("GREEN not saturated")
    Thread.sleep(waitMillis)
    light.saturation = 127
    Thread.sleep(waitMillis)
    light.saturation = 255
    print("-saturated ")
    Thread.sleep(waitMillis)
    light.hue = HueLightBulb.HUE_BLUE
    print("BLUE saturated")
    Thread.sleep(waitMillis)
    light.saturation = 127
    Thread.sleep(waitMillis)
    light.saturation = 0
    print("-not saturated ")
    Thread.sleep(waitMillis)
    light.hue = HueLightBulb.HUE_RED_2
    print("RED_2 not saturated")
    Thread.sleep(waitMillis)
    light.saturation = 127
    Thread.sleep(waitMillis)
    light.saturation = 255
    print("-saturated ")
    Thread.sleep(waitMillis)
    println()
}

def sweepBrightness(HueLightBulb light, int waitMillis) {
    println("sweepBrightness '$light.name'")
    light.brightness = 255
    Thread.sleep(waitMillis)
    light.brightness = 200
    Thread.sleep(waitMillis)
    light.brightness = 150
    Thread.sleep(waitMillis)
    light.brightness = 100
    Thread.sleep(waitMillis)
    light.brightness = 50
    Thread.sleep(waitMillis)
    light.brightness = 0
    Thread.sleep(waitMillis)
    light.brightness = 127
    Thread.sleep(waitMillis)
    light.brightness = 255
    Thread.sleep(waitMillis)
}

def blinkOnce(HueLightBulb light, int waitMillis) {
    println("blinkOnce '$light.name'")
    light.on = !light.on;
    Thread.sleep(waitMillis);
    light.on = !light.on;
    Thread.sleep(waitMillis);
}

def listLights(HueHub hub) {
    Collection<HueLightBulb> lights = hub.lights
    println(hub.name + " controls " + lights.size() + " lights:")
    for(HueLightBulb light : lights) {
        println(light)
    }
}

def discoverAndAuthenticate() {
    List<HueHub> hubs = HueHub.discover()
    for(HueHub hub : hubs) {
        println("Found " + hub)
        String u1 = 'IchBinHierUndWillWasVonDir'
        String u2 = '8aefa072a354a7f113f6bf72b173e6f'
        hub.username = u1;
        if(!hub.authenticate(false)) {
            println("Press the button on your Hue hub in the next 30 seconds to grant access.")
            if(hub.authenticate(true)) {
                println("Access granted. username: " + hub.username)
            } else {
                println("Authentication failed.")
            }
        } else {
            println("Already granted access. username: " + hub.username)
        }
    }
    return hubs
}
