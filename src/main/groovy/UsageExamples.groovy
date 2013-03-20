import com.jvmcode.philips.hue.HueHub

/**
 *
 * @author Stephan Jaetzold
 * <p><small>Created at 15.03.13, 16:23</small>
 */


discoverAndAuthenticate()

def discoverAndAuthenticate() {
    List<HueHub> hubs = HueHub.discover()
    for(HueHub hub : hubs) {
        println("Found hub at " + hub.baseUrl)
        String t1 = 'IchBinHierUndWillWasVonDir'
        String t2 = '8aefa072a354a7f113f6bf72b173e6f'
        hub.authToken = t1;
        if(!hub.authenticate(false)) {
            println("Press the button on your Hue hub in the next 30 seconds to grant access.")
            if(hub.authenticate(true)) {
                println("Access granted. authToken: " + hub.authToken)
            } else {
                println("Authentication failed.")
            }
        } else {
            println("Already granted access. authToken: " + hub.authToken)
        }
    }
    return hubs
}
