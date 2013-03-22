import com.jvmcode.philips.hue.HueHub
import com.jvmcode.philips.hue.HueLightBulb

/**
 *
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

blinkOnce(light)


def blinkOnce(HueLightBulb light) {
    println("Blink " +light.name)
    light.on = !light.on;
    Thread.sleep(1000);
    light.on = !light.on;
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
