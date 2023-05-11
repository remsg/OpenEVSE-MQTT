
/**
 *  OpenEVSE MQTT Device Handler
 *
 *  Loosely based on Garadget MQTT Driver
 *  https://raw.githubusercontent.com/jrfarrar/hubitat/master/devicehandlers/garadgetMQTT/garadgetmqtt.groovy
 *  and work from tomw's (https://github.com/tomwpublic) OpenEVSE hubitat client
 *  Tested with OpenEVSE WiFi v1 gui_v2 firmware v4.2.2, OpenEVSE v7.1.3
 *
 *  References:
 *    OpenEVSE url: https://openevse.stoplight.io/docs/openevse-wifi-v4/c03364fd24abd-mqtt
 *    OpenEVSE Firmware releases: https://github.com/OpenEVSE/open_evse/releases
 *    OpenEVSE WiFi Firmware releases: https://github.com/OpenEVSE/ESP32_WiFi_V4.x/releases
 *
 */

metadata {
    definition (name: "OpenEVSE MQTT",
                author: "remsg",
                namespace: "openevse",
               importUrl: "https://raw.githubusercontent.com/remsg/OpenEVSE-MQTT/main/openevse_mqtt.groovy") {
        capability "Initialize"
        capability "CurrentMeter"
        capability "EnergyMeter"
        capability "Initialize"
        capability "TemperatureMeasurement"
        capability "VoltageMeasurement"
        capability "PowerMeter"
        capability "PresenceSensor"
        capability "Switch"

        command "divertMode", [
            [name: 'mode', type: 'ENUM', constraints: ['Normal', 'Eco'], description: 'Eco = Solar Divert; Charge with excess solar output']
        ]
        command "enableManualOverride", [
            [name: 'state', type: 'ENUM', constraints: ['active', 'disabled']],
            [name: 'charge_current', type: 'NUMBER', description: 'Charge current >=0'],
            [name: 'max_current', type: 'NUMBER', description: 'Dynamically increase the max current'],
            [name: 'auto_release', type: 'ENUM', constraints: ['true', 'false']]
        ]
        command "toggleManualOverride"
        command "clearManualOverride"

        attribute "pilot", "number"
        attribute "divertmode", "number"
        attribute "state", "number"
        attribute "vehicle", "number"
        attribute "status", "string"
        attribute "amperage", "number"
        attribute "power", "number"
        attribute "voltage", "number"
        attribute "override", "string"
        attribute "temperature", "number"

preferences {
    section("Settings for connection from HE to Broker") {
        input name: "topicName", type: "text", title: "OpenEVSE Name(Topic name)", required: true
        input name: "ipAddr", type: "text", title: "IP Address of MQTT broker", required: true
        input name: "ipPort", type: "text", title: "Port # of MQTT broker", defaultValue: "1883", required: true
        input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false
	    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false
        input name: "retryTime", type: "number", title: "Number of seconds between retries to connect if broker goes down", defaultValue: 300, required: true
        input name: "refreshStats", type: "bool", title: "Refresh OpenEVSE stats on a schedule?", defaultValue: false, required: true
        input name: "refreshTime", type: "number", title: "If using refresh, refresh this number of minutes", defaultValue: 5, range: "1..59", required: true
        input name: "watchDogSched", type: "bool", title: "Check for connection to MQTT broker on a schedule?", defaultValue: false, required: true
        input name: "watchDogTime", type: "number", title: "This number of minutes to check for connection to MQTT broker", defaultValue: 15, range: "1..59", required: true
        input name: "logLevel",title: "IDE logging level",multiple: false,required: true,type: "enum", options: getLogLevels(), submitOnChange : false, defaultValue : "1"
//        input name: "ShowAllPreferences", type: "bool", title: "<b>Show OpenEVSE device settings?</b>These change settings on the device itself through MQTT", defaultValue: false
//        }
//   if( ShowAllPreferences ){
//    section("Settings for OpenEVSE"){
        // put configuration here
        input name: "throttle", type: "number", title: "Throttle stats to update every n sec unless charging", defaultValue: 60, range: "30..900", required: true
        input name: "throttleExtras", type: "bool", title: "Throttle temp events even while charging", defaultValue: true, required: true
        input name: "tempUnit", type: "bool", title: "Temperature in F", defaultValue: true, required: true
        input ( "max_current", "number", title: "<b>Max Current for Manual Override</b> (6-80, default 24)", defaultValue: 24,range: "6..80", required: false)
        input ( "charge_current", "number", title: "<b>Charge Current for Manual Override</b> (6-80, default 24)", defaultValue: 24,range: "6..80", required: false)
        input ( "auto_release", "bool", title: "<b>Auto-release when the vehicle is disconnected, false if manual override will persist after vehicle disconnection.</b> (true/false, default true)", defaultValue: 'true', required: false)
//        if (topicName && ipAddr && ipPort) input ( "auto_release", "bool", title: "<b>Auto-release when the vehicle is disconnected, false if manual override will persist after vehicle disconnection.</b> (true/false, default true)", defaultValue: 'true', required: false)
        }
   }
    /*
    section("Logging"){
        //logging
        input(name: "logLevel",title: "IDE logging level",multiple: false,required: true,type: "enum", options: getLogLevels(), submitOnChange : false, defaultValue : "1")
        }
    */
    }
}

def setVersion(){
    //state.name = "Garadget MQTT"
	state.version = "0.0.5 - OpenEVSE MQTT Device Handler version"
}

void installed() {
    log.warn "installed..."
}

// Parse incoming device messages to generate events
void parse(String description) {
    topicFull=interfaces.mqtt.parseMessage(description).topic
	def topic=topicFull.split('/')
    /*
	//def topicCount=topic.size()
	//def payload=interfaces.mqtt.parseMessage(description).payload.split(',')
    //log.debug "Desc.payload: " + interfaces.mqtt.parseMessage(description).payload
    //if (payload[0].startsWith ('{')) json="true" else json="false"
    //log.debug "json= " + json
    //log.debug "topic0: " + topic[0]
    //log.debug "topic1: " + topic[1]
    //debuglog "topic2: " + topic[2]
    //top=interfaces.mqtt.parseMessage(description).topic
    */

    def value=interfaces.mqtt.parseMessage(description).payload
    if (value) {
//        debuglog "Topic ${topic[1]} arrived with value ${value}"
        updateTopic(topic[1], value)
    } else {
        debuglog "Empty payload: \"" + value + "\""
    }
}
void updateTopic(topic, value) {
    currentValue = state.lastValue[topic]

    if (currentValue != null) {
        if ( !(topic.equals("state") && value == 3) || !(topic.equals("amp") && value == 0)) {
            value = throttleUpdate(topic, value, currentValue)
        }
        if ( throttleExtras ) {
            switch(topic) {
                case "temp2":
                    value = throttleUpdate(topic, value, currentValue)
                case "temp4":
                    value = throttleUpdate(topic, value, currentValue)
            }
        }
    }

    if (value != currentValue) {
        sendEvent(name: topic, value: value)
        state.lastValue[topic] = value
        debuglog "Topic: " + topic + " Value: \"" + value + "\" Current value: \"" + currentValue + "\""
        switch(topic) {
            case "amp":
                sendEvent(name: "amperage", value:  (null != value) ? (value).toInteger() / 1000 : "n/a")
            case "temp2":
                value = (null != value) ? value.toInteger() / 10 : "n/a"
                temp = tempUnit ? celsiusToFahrenheit(value) : value
                sendEvent(name: "temperature", value: temp)
            case "vehicle":
                def presenceMap = ["1":"present", "0":"not present"]
                sendEvent(name: "presence", value:  (null != value) ? presenceMap[value] : "n/a")
            case "divertmode":
                def divertMap = ["1":"Normal", "2":"Eco"]
                sendEvent(name: "Divert", value: (null != value) ? divertMap[value] : "n/a")
        }
    }
}

def throttleUpdate(topic, value, lastValue) {
    timenow = now()
    state.lastUpdate[topic] = (null == state.lastUpdate[topic]) ? 1 : state.lastUpdate[topic]
    delta = timenow - state.lastUpdate[topic]
 //   debuglog "${topic} last update before: ${state.lastUpdate[topic]} now ${timenow} delta: ${delta} threshold: ${state.throttle_ms} incoming value: ${value} previous value: ${lastValue}"
    if(timenow - state.lastUpdate[topic] <= state.throttle_ms) {
        debuglog "Throttled ${topic}, returning previous value ${lastValue}"
        return lastValue
    }
    state.lastUpdate[topic] = timenow
//    debuglog "Last update now: ${state.lastUpdate[topic]} value is now ${value}"
    return value
}

void updated() {
    infolog "updated..."
    //set schedules
    unschedule()
    state.clear()
    pauseExecution(1000)
    initializeLastUpdate()
    //schedule the watchdog to run in case the broker restarts
    if (watchDogSched) {
        debuglog "setting schedule to check for MQTT broker connection every ${watchDogTime} minutes"
        schedule("44 7/${watchDogTime} * ? * *", watchDog)
    }
    //If refresh set to true then set the schedule
    if (refreshStats) {
        debuglog "setting schedule to refresh every ${refreshTime} minutes"
        schedule("22 3/${refreshTime} * ? * *", requestStatus)
    }
    state.throttle_ms = throttle * 1000
}
void uninstalled() {
    infolog "disconnecting from mqtt..."
    interfaces.mqtt.disconnect()
    unschedule()
}

def divertMode( mode )
{
    watchDog()
    initializeLastUpdate()
    def divertMap = ["Normal":"1", "Eco":"2"]
    def divertMode = divertMap[mode]
    infolog "setting divert mode to ${mode} ${divertMode}"
    state.lastUpdate["charge_rate"] = 1
    updateTopic("charge_rate", "0")
    interfaces.mqtt.publish("${topicName}/divertmode", divertMode)
}
def enableManualOverride( Ostate, Ocharge_current, Omax_current, Oauto_release )
{
    watchDog()
    initializeLastUpdate()
    def overrideOptions = [:]
    overrideOptions.state = Ostate
    overrideOptions.charge_current = (null != Ocharge_current) ? Ocharge_current.toString() : charge_current.toString()
    overrideOptions.max_current = (null != Omax_current) ? Omax_current.toString() : max_current.toString()
    overrideOptions.auto_release = ('true' == Oauto_release) ? true : false
    def json = new groovy.json.JsonOutput().toJson(overrideOptions)
    infolog "enabling manual override ${json}"
    interfaces.mqtt.publish("${topicName}/override/set", json)
}

def toggleManualOverride()
{
    watchDog()
    initializeLastUpdate()
    infolog "toggling manual override"
    interfaces.mqtt.publish("${topicName}/override/set", "toggle")
}

def clearManualOverride()
{
    watchDog()
    initializeLastUpdate()
    infolog "clearing manual override"
    interfaces.mqtt.publish("${topicName}/override/set", "clear")
}

def on()
{
    watchDog()
    initializeLastUpdate()
    def overrideOptions = [:]
    overrideOptions.state = 'active'
    overrideOptions.charge_current = charge_current.toString()
    overrideOptions.max_current = max_current.toString()
    overrideOptions.auto_release = auto_release ? true : false
    def json = new groovy.json.JsonOutput().toJson(overrideOptions)
    infolog "turning on using manual override ${json}"
    interfaces.mqtt.publish("${topicName}/override/set", json)
}

def off()
{
    watchDog()
    initializeLastUpdate()
    def overrideOptions = [:]
    overrideOptions.state = 'disabled'
    overrideOptions.charge_current = charge_current.toString()
    overrideOptions.max_current = max_current.toString()
    overrideOptions.auto_release = auto_release ? true : false
    def json = new groovy.json.JsonOutput().toJson(overrideOptions)
    infolog "turning off using manual override ${json}"
    interfaces.mqtt.publish("${topicName}/override/set", json)
}

void initialize() {
    infolog "initialize..."
    try {
        mqttInt.disconnect()
        state.clear()
    } catch (e) {
        log.warn "${device.label?device.label:device.name}: MQTT disconnect error: ${e.message}"
    }
    try {
        //open connection
        def mqttInt = interfaces.mqtt
        mqttbroker = "tcp://" + ipAddr + ":" + ipPort
        mqttclientname = "Hubitat MQTT " + topicName
        mqttInt.connect(mqttbroker, mqttclientname, username,password)
        //give it a chance to start
        pauseExecution(1000)
        infolog "connection established..."
        topicList = [
            "charge_rate",    // Current available for Eco Divert
            "override",    // Override Status {state:active/disabled, charge_current:>=0, max_current:>=0, auto_release:true/false}
            "pilot",        // Charge current allowed in Amps
            "divertmode",    // Divert Mode 1-Normal, 2-Eco (Solar) Divert
            "state",        //EVSE State 1-Ready, 2-Connected, 3-Charging, 4-Error, 254-Disabled
            "vehicle",       // 1 if plugged into vehicle
            "amp",            // Measured Current in milliamps
            "power",        // power
            "voltage",        // Voltage
            "temp2",            // temperatuce in 10th degree C
            "temp4",            // temperatuce in 10th degree C
            "freeram",            // ESP32 Free Ram
            "status"        // EVSE Status: Active, Disabled
        ]
        //subscribe to status and config topics
        topicList.each {
            debuglog "Subscribing to: ${topicName}/${it}"
            mqttInt.subscribe("${topicName}/${it}")
        }
        initializeLastUpdate()
        state.throttle_ms = throttle * 1000

    } catch(e) {
        log.warn "${device.label?device.label:device.name}: MQTT initialize error: ${e.message}"
    }
    //if logs are in "Need Help" turn down to "Running" after an hour
    logL = logLevel.toInteger()
    if (logL == 2) runIn(3600,logsOff)
}

def initializeLastUpdate() {
    state.lastUpdate = [:]
    state.lastValue = [:]
    topicList.each {
        state.lastUpdate[it] = 1
        state.lastValue[it] = null
        debuglog "initializing ${it} to ${state.lastUpdate[it]} and ${state.lastValue[it]}"
    }
}

def watchDog() {
    debuglog "Checking MQTT status"
    //if not connnected, re-initialize
    if(!interfaces.mqtt.isConnected()) {
        debuglog "MQTT Connected: (${interfaces.mqtt.isConnected()})"
        initialize()
    }
    state.throttle_ms = throttle * 1000
}
void mqttClientStatus(String message) {
	log.warn "${device.label?device.label:device.name}: **** Received status message: ${message} ****"
    if (message.contains ("Connection lost")) {
        connectionLost()
    }
}
//if connection is dropped, try to reconnect every (retryTime) seconds until the connection is back
void connectionLost(){
    //convert to milliseconds
    delayTime = retryTime * 1000
    while(!interfaces.mqtt.isConnected()) {
        infolog "connection lost attempting to reconnect..."
        initialize()
        pauseExecution(delayTime)
    }
}

//Logging below here
def logsOff(){
    log.warn "debug logging disabled"
    device.updateSetting("logLevel", [value: "1", type: "enum"])
}
def debuglog(statement)
{
	def logL = 0
    if (logLevel) logL = logLevel.toInteger()
    if (logL == 0) {return}//bail
    else if (logL >= 2)
	{
		log.debug("${device.label?device.label:device.name}: " + statement)
	}
}
def infolog(statement)
{
	def logL = 0
    if (logLevel) logL = logLevel.toInteger()
    if (logL == 0) {return}//bail
    else if (logL >= 1)
	{
		log.info("${device.label?device.label:device.name}: " + statement)
	}
}
def getLogLevels(){
    return [["0":"None"],["1":"Running"],["2":"NeedHelp"]]
}
