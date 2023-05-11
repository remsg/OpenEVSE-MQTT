
/**
 *  OpenEVSE MQTT Device Handler
 *
 *  Loosely based on Garadget MQTT Driver
 *  https://raw.githubusercontent.com/jrfarrar/hubitat/master/devicehandlers/garadgetMQTT/garadgetmqtt.groovy
 *  and work from tomw's (https://github.com/tomwpublic) OpenEVSE hubitat client
 *  Tested with OpenEVSE WiFi v1 gui_v2 firmware v4.2.2, OpenEVSE v7.1.3
 */

metadata {
    definition (name: "OpenEVSE MQTT",
                author: "remsg",
                namespace: "openevse",
               importUrl: "") {
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
    }
}
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
        input name: "ShowAllPreferences", type: "bool", title: "<b>Show OpenEVSE device settings?</b>These change settings on the device itself through MQTT", defaultValue: false
        }
   if( ShowAllPreferences ){
    section("Settings for OpenEVSE"){
        // put configuration here
        if (topicName && ipAddr && ipPort) input ( "max_current", "number", title: "<b>OpenEVSE: Max Current</b> (6-80, default 24)", defaultValue: 24,range: "6..80", required: false)
        }
   }
    /*
    section("Logging"){
        //logging
        input(name: "logLevel",title: "IDE logging level",multiple: false,required: true,type: "enum", options: getLogLevels(), submitOnChange : false, defaultValue : "1")
        }
    */
}

def setVersion(){
    //state.name = "Garadget MQTT"
	state.version = "0.0.4 - OpenEVSE MQTT Device Handler version"
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
        updateTopic(topic[1], value)
    } else {
        debuglog "Empty payload: \"" + value + "\""
    }
}
void updateTopic(topic, value) {

    if (value != device.currentValue(topic)) {

        debuglog "Topic: " + topic + " Value: " + value + " Current value: " + device.currentValue(topic)
        sendEvent(name: topic, value: value)
        switch(topic) {
            case "amp":
                sendEvent(name: "amperage", value:  (null != value) ? (value).toInteger() / 1000 : "n/a")
            case "temp2":
                sendEvent(name: "temperature", value:  (null != value) ? (value).toInteger() / 10 : "n/a")
            case "vehicle":
                def presenceMap = ["1":"present", "0":"not present"]
                sendEvent(name: "presence", value:  (null != value) ? presenceMap[value] : "n/a")
            case "divertmode":
                def divertMap = ["1":"Normal", "2":"Eco"]
                debuglog "Divert Mode" + divertMap[value]
                sendEvent(name: "Divert", value: (null != value) ? divertMap[value] : "n/a")
        }
    }
}

//Handle config update topic
void getConfig(config) {
    //
    //Set some states for Garadget/Particle Info
    //
    debuglog "sys: " + config.sys + " - Particle Firmware Version"
    state.sys = config.sys + " - Particle Firmware Version"
    debuglog "ver: " + config.ver + " - Garadget firmware version"
    state.ver = config.ver + " - Garadget firmware version"
    debuglog "id: "  + config.id  + " - Garadget/Particle device ID"
    state.id = config.id  + " - Garadget/Particle device ID"
    debuglog "ssid: "+ config.ssid + " - WiFi SSID name"
    state.ssid = config.ssid + " - WiFi SSID name"
    //
    //refresh and update configuration values
    //
    debuglog "rdt: " + config.rdt + " - sensor scan interval in mS (200-60,000, default 1,000)"
    rdt = config.rdt
    device.updateSetting("rdt", [value: "${rdt}", type: "number"])
    sendEvent(name: "rdt", value: rdt)
    //
    debuglog "mtt: " + config.mtt + " - door moving time in mS from completely opened to completely closed (1,000 - 120,000, default 10,000)"
    mtt = config.mtt
    device.updateSetting("mtt", [value: "${mtt}", type: "number"])
    sendEvent(name: "mtt", value: mtt)
    //
    debuglog "rlt: " + config.rlt + " - button press time mS, time for relay to keep contacts closed (10-2,000, default 300)"
    rlt = config.rlt
    device.updateSetting("rlt", [value: "${rlt}", type: "number"])
    sendEvent(name: "rlt", value: rlt)
    //
    debuglog "rlp: " + config.rlp + " - delay between consecutive button presses in mS (10-5,000 default 1,000)"
    rlp = config.rlp
    device.updateSetting("rlp", [value: "${rlp}", type: "number"])
    sendEvent(name: "rlp", value: rlp)
    //
    debuglog "srt: " + config.srt + " - reflection threshold below which the door is considered open (1-80, default 25)"
    srt = config.srt
    device.updateSetting("srt", [value: "${srt}", type: "number"])
    sendEvent(name: "srt", value: srt)
    //
    //nme is currently broken in Garadget firmware 1.2 - it does not honor it. It uses default device name.
    debuglog "nme: " + config.nme + " - device name to be used in MQTT topic."
    //nme = config.nme
    //device.updateSetting("nme", [value: "${nme}", type: "text"])
    //sendEvent(name: "nme", value: nme")
    //
    //Not tested setting the bitmap from HE - needs to be tested
    debuglog "mqtt: " + config.mqtt + " - bitmap 0x01 - cloud enabled, 0x02 - mqtt enabled, 0x03 - cloud and mqtt enabled"
    //mqtt = config.mqtt
    //device.updateSetting("mqtt", [value: "${mqtt}", type: "text"])
    //sendEvent(name: "mqtt", value: mqtt")
    //
    debuglog "mqip: " + config.mqip + " - MQTT broker IP address"
    mqip = config.mqip
    device.updateSetting("mqip", [value: "${mqip}", type: "text"])
    sendEvent(name: "mqip", value: mqip)
    //
    debuglog "mqpt: " + config.mqpt + " - MQTT broker port number"
    mqpt = config.mqpt
    device.updateSetting("mqpt", [value: "${mqpt}", type: "number"])
    sendEvent(name: "mqpt", value: mqpt)
    //
    //See no need to implement changing the username as you can't change the password via the MQTT interface
    debuglog "mqus: " + config.mqus + " - MQTT user"
    //mqus = config.mqus
    //
    debuglog "mqto: " + config.mqto + " - MQTT timeout (keep alive) in seconds"
    mqto = config.mqto
    device.updateSetting("mqto", [value: "${mqto}", type: "number"])
    sendEvent(name: "mqto", value: mqto)
}

void updated() {
    infolog "updated..."
    //set schedules
    unschedule()
    pauseExecution(1000)
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
}
void uninstalled() {
    infolog "disconnecting from mqtt..."
    interfaces.mqtt.disconnect()
    unschedule()
}

def divertMode( mode )
{
    watchDog()
    def divertMap = ["Normal":"1", "Eco":"2"]
    infolog "setting divert mode to ${mode} ${divertMap[mode]}"
    interfaces.mqtt.publish("${topicName}/divertmode", divertMap[mode])
}
def enableManualOverride( Ostate, Ocharge_current, Omax_current, Oauto_release )
{
    watchDog()
    def overrideOptions = [:]
    overrideOptions.state = Ostate
    overrideOptions.charge_current = (null != Ocharge_current) ? Ocharge_current.toString() : '24'
    overrideOptions.max_current = (null != Omax_current) ? Omax_current.toString() : '24'
    overrideOptions.auto_release = Oauto_release
    def json = new groovy.json.JsonOutput().toJson(overrideOptions)
    infolog "enabling manual override ${json}"
    interfaces.mqtt.publish("${topicName}/override/set", json)
}

def toggleManualOverride()
{
    watchDog()
    infolog "toggling manual override"
    interfaces.mqtt.publish("${topicName}/override/set", "toggle")
}

def clearManualOverride()
{
    watchDog()
    infolog "clearing manual override"
    interfaces.mqtt.publish("${topicName}/override/set", "clear")
}

def on()
{
    watchDog()
    def overrideOptions = [:]
    overrideOptions.state = 'active'
    overrideOptions.charge_current = '24'
    overrideOptions.max_current = '24'
    overrideOptions.auto_release = 'true'
    def json = new groovy.json.JsonOutput().toJson(overrideOptions)
    infolog "turning on using manual override ${json}"
    interfaces.mqtt.publish("${topicName}/override/set", json)
}

def off()
{
    watchDog()
    def overrideOptions = [:]
    overrideOptions.state = 'disabled'
    overrideOptions.charge_current = '24'
    overrideOptions.max_current = '24'
    overrideOptions.auto_release = 'true'
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
        def topicList = [
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

    } catch(e) {
        log.warn "${device.label?device.label:device.name}: MQTT initialize error: ${e.message}"
    }
    //if logs are in "Need Help" turn down to "Running" after an hour
    logL = logLevel.toInteger()
    if (logL == 2) runIn(3600,logsOff)
}

def watchDog() {
    debuglog "Checking MQTT status"
    //if not connnected, re-initialize
    if(!interfaces.mqtt.isConnected()) {
        debuglog "MQTT Connected: (${interfaces.mqtt.isConnected()})"
        initialize()
    }
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
