import de.jaetzold.philips.hue.HueBridge
import de.jaetzold.philips.hue.HueLight
import de.jaetzold.philips.hue.HueLightBulb
import de.jaetzold.philips.hue.HueLightGroup
import de.jaetzold.philips.hue.HueVirtualLightGroup

/**
 * A simple Demo/Test script for the philips-hue-java-sdk
 *
 Future
 * Group creation/deletion when Bridge API supports that.
 * Schedules support.
 * Extended configuration support (e.g. list/delete Users)
 * Maybe get transactions to be able to completely rollback. Especially with nested transactions involved it is currently possible that
   an inner transaction is already committed if an outer transaction fails. And the bridge may only have success in setting part of the state
   that got committed. Maybe the state before could be remembered an then set back? This is not so easy to ensure...
 * Fire events for lights on (even external) state changes. External can be enabled and disabled to preserve resources.
 * Fire events on available lights changes/bridge state changes. External can be enabled and disabled to preserve resources.

 * @author Stephan Jaetzold
 * <p><small>Created at 15.03.13, 16:23</small>
 */

/* Discover and connect to a hue bridge. May be necessary to press the button on the bridge the first time this is used. */
List<HueBridge> bridges = discoverAndAuthenticate()

if(bridges.isEmpty()) {
    println("No bridge found.")
    System.exit(1)
}
HueBridge bridge = bridges.get(0)

/* Query for all lights on the bridge */
listLights(bridge)
/* Query for all groups on the bridge */
listGroups(bridge)

/* Change bridge state */
bridge.setName("Jaetzold's Hue")
println("After changing name: " +bridge)

if(bridge.lights.isEmpty()) {
    println("No lights found.")
    System.exit(2)
}
HueLightBulb light = bridge.getLight(bridge.lightIds[0])

/* Change various light states */
light.on = true;
blinkOnce(light, 1000)
HueLightGroup group = bridge.getGroup(0)
blinkOnce(group, 1000)      // Groups and LightBulbs share the same interface (containing only the state setters though)

light.transitionTime = 1    // this is used in all subsequent (non-transactional) state changes. 'null' uses the bridges default

sweepBrightness(light, 1000)
sweepHueSaturation(light, 1000)
sweepCieXY(light, 1000)
sweepColorTemperature(light, 1000)

multipleStateChangeInOneRequest(light, 4000)

alert(light, 10000)
effect(light, 10000)

/* Use virtual groups to group any HueLight (Groups, VirtualGroups and LightBulbs) on the client side */
createVirtualGroup(bridge, light)

/*
Since no events are supported currently by the API implementation, reacting to change requires manual polling.
Tune the performance of this by modifying HueLightBulb.setAutoSyncInterval(Integer)
*/
pollForExternalStateChanges(light, 30000)

/* How to get the bridge to recognize newly added light bulbs. Not tested. */
searchForNewLights(bridge)

// *****************************************
// Demo methods
// *****************************************

def searchForNewLights(HueBridge bridge) {
    def active = bridge.scanActive
    println((active ? "A" : "No")+" search is currently active")
    if(!active) {
        println("Starting search for new lights")
        bridge.searchForNewLights()
    }
    def start = System.currentTimeMillis()
    while(System.currentTimeMillis()-start<65000) {
        Thread.sleep(2000)
        Collection<? extends HueLightBulb> newLights = bridge.getNewLights()
        if(!newLights) {
            if(bridge.scanActive) {
                print(".")
            } else {
                println("No new lights found.")
                break;
            }
        } else {
            println(bridge.name + " found " + newLights.size() + " new lights:")
            for(HueLightBulb light : newLights) {
                println(light)
            }
        }
    }
}

def pollForExternalStateChanges(HueLightBulb light, int pollMillis) {
    def start = System.currentTimeMillis()
    boolean lastOn = light.on
    print("polling on state changes for $light: ")
    while(System.currentTimeMillis()-start<pollMillis) {
        boolean nowOn = light.on
        if(nowOn!=lastOn) {
            print(nowOn ? "ON " : "OFF ")
        }
        lastOn = nowOn
    }
    println()
}

def createVirtualGroup(HueBridge bridge, HueLightBulb light) {
    if(!bridge.lightIds.contains(3)) {
        println("Did not find expected light with id=3.")
        System.exit(3)
    }
    HueVirtualLightGroup virtualGroup = new HueVirtualLightGroup(bridge, 1, "Virtual group with a very long name", light)
    HueVirtualLightGroup virtualGroup2 = new HueVirtualLightGroup(bridge, 2, "Subgroup", bridge.getLight(3))
    virtualGroup.add(virtualGroup2)
    listVirtualGroups(bridge)
    virtualGroup.transitionTime = 1
    blinkOnce(virtualGroup, 1000)
    virtualGroup.stateChangeTransaction(2000) {
        virtualGroup.saturation = 0
        virtualGroup.colorTemperature = 400
        virtualGroup.brightness = 255
    }
}

def listVirtualGroups(HueBridge bridge) {
    Collection<HueVirtualLightGroup> virtualGroups = bridge.virtualGroups
    println(bridge.name + " has " + virtualGroups.size() + " virtual groups:")
    for(HueVirtualLightGroup group : virtualGroups) {
        println(group)
    }
}

def effect(HueLightBulb light, int waitMillis) {
    println("effect '$light.name'")
    light.effect = HueLight.Effect.COLORLOOP
    Thread.sleep(waitMillis)
    light.effect = HueLight.Effect.NONE
}

def alert(HueLightBulb light, int waitMillis) {
    println("alert '$light.name'")
    light.alert = HueLight.Alert.LSELECT
    Thread.sleep(waitMillis)
    light.alert = HueLight.Alert.NONE
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

def blinkOnce(HueLight light, int waitMillis) {
    println("blinkOnce '$light.name'")
    light.on = false;
    Thread.sleep(waitMillis);
    light.on = true;
    Thread.sleep(waitMillis);
}

def listGroups(HueBridge bridge) {
    Collection<? extends HueLightGroup> groups = bridge.groups
    println(bridge.name + " has " + groups.size() + " groups:")
    for(HueLightGroup group : groups) {
        println(group)
    }
}

def listLights(HueBridge bridge) {
    Collection<? extends HueLightBulb> lights = bridge.lights
    println(bridge.name + " controls " + lights.size() + " lights:")
    for(HueLightBulb light : lights) {
        println(light)
    }
}

def discoverAndAuthenticate() {
    List<HueBridge> bridges = HueBridge.discover()
    for(HueBridge bridge : bridges) {
        println("Found " + bridge)
        // You may need a better scheme to store your username that to just hardcode it.
        // suggestion: Save a mapping from HueBridge.getUDN() to HueBridge.getUsername() somewhere.
        bridge.username = '8aefa072a354a7f113f6bf72b173e6f';
        if(!bridge.authenticate(false)) {
            println("Press the button on your Hue bridge in the next 30 seconds to grant access.")
            if(bridge.authenticate(true)) {
                println("Access granted. username: " + bridge.username)
            } else {
                println("Authentication failed.")
            }
        } else {
            println("Already granted access. username: " + bridge.username)
        }
    }
    return bridges
}
