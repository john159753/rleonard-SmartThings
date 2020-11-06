/**
 *  SmartStart Car
 *
 *  Copyright 2020 John Daley
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
preferences {

 input(
  "refresh_rate",
  "enum",
  title: "State Refresh Rate",
  options: [
   "Every 5 minutes",
   "Every 10 minutes",
   "Every 15 minutes",
   "Every 30 minutes",
   "Every hour",
   "Every 3 hours",
   "Disabled"
  ],
  description: "Refresh Interval of vehicle information.",
  required: true,
  defaultValue: "Every 3 hours")
}

metadata {
 definition(name: "SmartStart Car", namespace: "john159753", author: "John Daley", mnmn: "SmartThings", vid:"generic-switch") {
  capability "Switch"
  capability "Lock"
  capability "Refresh"
  capability "Power Meter"
  capability "Sensor"
  capability "Actuator"
  capability "Geolocation"
  command "EngineOn"
  command "EngineOff"
 }




 tiles(scale: 2) {
  // standard tile with actions named
  standardTile("lock", "device.lock", width: 2, height: 2, canChangeIcon: true) {
   state "unlocked", label: '${currentValue}', action: "lock.lock",
    icon: "st.Transportation.transportation8", backgroundColor: "#ffffff"
   state "locked", label: '${currentValue}', action: "lock.unlock",
    icon: "st.Transportation.transportation8", backgroundColor: "#00a0dc"
  }
  standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
   state "off", label: 'Engine Off', action: "switch.off",
    icon: "st.Transportation.transportation8", backgroundColor: "#ffffff"
   state "on", label: 'Engine On', action: "switch.on",
    icon: "st.Transportation.transportation8", backgroundColor: "#00a0dc"
  }

  standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
   state "default", label: "", action: "refresh.refresh", icon: "st.secondary.refresh"
  }


  // value tile (read only)
  valueTile("power", "device.power", decoration: "flat", width: 2, height: 2) {
   state "power", label: '${currentValue} Watts'
  }


  // the "switch" tile will appear in the Things view
  main("lock")

  // the "switch" and "power" tiles will appear in the Device Details
  // view (order is left-to-right, top-to-bottom)
  details(["lock", "switch", "refresh", "power"])
 }

}
def installed() {
 checkRefresh(settings)
}
def updated() {
 checkRefresh(settings)
}

def checkRefresh(settings) {
 switch (settings.refresh_rate?.toLowerCase()) {
  case "disabled":
   unschedule(doRefresh)
   doRefresh()
   break
  case "every 5 minutes":
   runEvery5Minutes(doRefresh)
   break
  case "every 10 minutes":
   runEvery10Minutes(doRefresh)
   break
  case "every 15 minutes":
   runEvery15Minutes(doRefresh)
   break
  case "every 30 minutes":
   runEvery30Minutes(doRefresh)
   break
  case "every hour":
   runEvery1Hour(doRefresh)
   break
  case "every 3 hours":
   runEvery3Hours(doRefresh)
   break
  default:
   runEvery3Hours(doRefresh)
   break
 }
}
def doRefresh() {

 fakeDataTest(parent.GetRandomNumber().toInteger())
 parent.pollStatus(device.deviceNetworkId)


}
def fakeDataTest(randomNumber) {
  sendEvent(name: "power", value: randomNumber)
 }
 
 // parse events into attributes
def parseEventData(Map results) {
 if (results?.error == "TokenExpiredRetry") {
  log.debug "Token Refreshed, Repolling Data"
  runIn(10, doRefresh)
 } else {
  results.each {
   name,
   value ->
   log.debug "Event Name: $name - Event Value: $value"

  }

  if (results["locked"] == true) {
sendEvent(name: "lock", value: "locked")
  } else {
sendEvent(name: "lock", value: "unlocked")
  }

  if (results["running"] == true) {
   sendEvent(name: "switch", value: "on")
  } else {
   sendEvent(name: "switch", value: "off")
  }
	   sendEvent(name: "latitude", value: results["latitude"])
          sendEvent(name: "longitude", value: results["longitude"])
 }
}
def refresh() {
  doRefresh()
 }
 // handle commands
def retryCmd(cmd) {
 log.debug "Token Refreshed, Resending Command Data"
 parent.sendCommand(cmd, device.deviceNetworkId)
 runIn(15, doRefresh)
}

def EngineOn() {
 sendEvent(name: "switch", value: "on")
 log.debug "Executing 'Engine on'"
 parent.sendCommand("Start", device.deviceNetworkId)
 runIn(15, doRefresh)
 runIn(3600, doRefresh)
}

def EngineOff() {
 sendEvent(name: "switch", value: "off")
 log.debug "Executing 'Engine off'"
 parent.sendCommand("Start", device.deviceNetworkId)
 runIn(15, doRefresh)
 runIn(3600, doRefresh)
}
def lock() {
sendEvent(name: "lock", value: "locked")
 log.debug "Executing 'Lock Doors'"
 parent.sendCommand("Lock", device.deviceNetworkId)
 runIn(15, doRefresh)
}

def unlock() {
sendEvent(name: "lock", value: "unlocked")
 log.debug "Executing 'Unlock Doors'"
 parent.sendCommand("Unlock", device.deviceNetworkId)
 runIn(15, doRefresh)
}

def on() {
    EngineOn()
}

def off() {
    EngineOff()
}
