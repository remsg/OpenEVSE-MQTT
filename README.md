# OpenEVSE MQTT
OpenEVSE device handler using MQTT for Hubitat
This started as jrfarrar's GaradgetMQTT DH (https://github.com/jrfarrar/hubitat/tree/master/devicehandlers/garadgetMQTT)
This also leverages some of the work from tomw (https://github.com/tomwpublic)

Tested with OpenEVSE WiFi v1 gui_v2 firmware v4.2.2, OpenEVSE v7.1.3, Hubitat c7 version 2.3.5.125

At this point, the Preferences haven't been modified for OpenEVSE, however the base functionality for triggering and controlling OpenEVSE is present.

The exposed MQTT states are:
- amp (also amperage)
- divertmode (also Divert)
- freeram
- override
- pilot
- power
- state
- status
- temp2 (also temperature)
- temp4
- vehicle

Switch functionality is exposed using manual override functions (state=disable for off and state=active for on).  In both cases, current is defaulted to 24A.  Auto_release is also set to clear override on unplug.
