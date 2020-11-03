/**
 *  SmartStart App
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
import groovy.json.JsonSlurper
include 'asynchttp_v1'

definition(
 name: "SmartStart",
 namespace: "john159753",
 author: "John Daley",
 description: "SmartStart SmartApp",
 category: "Convenience",
 iconUrl: "https://emojipedia-us.s3.amazonaws.com/thumbs/160/apple/129/pile-of-poo_1f4a9.png",
 iconX2Url: "https://emojipedia-us.s3.amazonaws.com/thumbs/160/apple/129/pile-of-poo_1f4a9.png",
 iconX3Url: "https://emojipedia-us.s3.amazonaws.com/thumbs/160/apple/129/pile-of-poo_1f4a9.png",
 singleInstance: true) 
 {
 appSetting "Username"
 appSetting "Password"
 

}


def getApiEndpoint() {
 return "https://www.vcp.cloud"
}
preferences {
  
     
    
        page(name: "mainPage", title: "Simple Automations", install: true, uninstall: true,submitOnChange: true) {
      section("SmartStart Credentials") {
             input("Username", "text", title:"SmartStart Username", description: "Your SmartStart Username" , required: true, displayDuringSetup: true)
        input("Password", "password", title:"SmartStart Password", description: "Your SmartStart Password", required: true, displayDuringSetup: true)
    }
    }
}



def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    Date latestdate = new Date(); 
	log.debug "Current Token: ${state.token} - Lifespan: ${state.tokenexpiry}"
    
    if(state.token == null )
    {
    
        //Need new SmartStartToken
        GetToken()
    }
    else if(state.tokenexpiry - latestdate.getTime() <0)
    {
    log.debug "MS left on token: ${state.tokenexpiry - latestdate.getTime()}"
            //Need new SmartStartToken
        GetToken()
    }
    
    //Get Devices From SmartStart
	GetCars()
    
    
        def carList = state.carChild ?: [:]
    def cars = state.carList.collect {
    
          dni ->
          // log.debug "PLEASE HERE: ${dni}"
          def children = app.getChildDevices()
log.debug children.find{iee -> iee.deviceNetworkId == String.valueOf(dni.id)}
            def childDevice = children.find{it.deviceNetworkId==String.valueOf(dni.id)}

            if (!childDevice) {
                childDevice = addChildDevice(app.namespace, "SmartStart Car", String.valueOf(dni.id), null, [name: "SmartStart.${dni.id}", label: "${dni.vehicleMake} ${dni.vehicleModel}" ?: "nullname", completedSetup: true])
                childDevice.doRefresh()
                log.debug "Device Creation - created ${childDevice.displayName} with id $dni.id"
                
            } else {
            	log.debug "Device Creation - ${childDevice.displayName} with id $dni.id already exists"
            }
            return childDevice
    }
    
    
}

//Called when smartapp is uninstalled
def uninstalled() {

	removeChildDevices(getChildDevices())
}

def GetCars(){
//Check if token needed
Date latestdate = new Date(); 
if(state.tokenexpiry - latestdate.getTime() <0)
    {
    log.debug "MS left on token: ${state.tokenexpiry - latestdate.getTime()}"
            //Need new SmartStartToken
        GetToken()
    }

                def getCarListParams = [
            uri: apiEndpoint + "/v1/devices/search/null?limit=100&deviceFilter=Installed&subAccounts=false",
            headers: ["Content-Type": "application/json;charset=UTF-8", "Authorization": "Bearer ${state.token}"]
        ]
           try{
        httpGet(getCarListParams) {
            resp ->
                log.debug "Cars http status: ${resp.status}"
                
                if (resp.status == 200) {
                	
                    def carList = resp.data
                    
            		state.carList = carList.results.devices
                    

                } else {
                	log.error "Unable to get cars!"
                }
		}
        } catch (e) {
            log.debug "something went wrong:  e:${e}, data:${e.response?.data}"
        }
}

def GetToken(){


   try{
        httpPost(apiEndpoint + "/v1/auth/login", "username=${Username}&password=${Password}")  {
            resp ->
                log.debug "Token http status: ${resp.status}"
                
                if (resp.status == 200) {
                	
                    def token = resp.data
                    state.token = token.results.authToken.accessToken
                    state.tokenexpiry = token.results.authToken.expiration
                    log.debug token

                } else {
                	log.error "Unable to get token!"
                }
		}
        } catch (e) {
            log.debug "something went wrong: e:${e}, data:${e.response?.data}"
        }
}



//Helpful Debugger Method
def GetRandomNumber() {
     new Random().nextInt(10100)
}


//Response Method for Async HTTP Request
def postWithJsonHandler(response, data) {
    if (response.hasError()) {
        log.error "RemoteStart - Response has error: $response.errorMessage"
        log.error "RemoteStart - Response has data: ${response.errorJson?.'ResponseStatus'}"
  }
  else{
        def results
        try {
            results = response.json.results.device
        } catch (e) {
        	log.error "RemoteStart - Error parsing json from response: $e"
        }
        log.debug results
        if (results.find {it.toString().contains('securitySystemArmed')} as Boolean) { // Then this means its a status response
        	
            def statusdata = [
                locked: results?.deviceStatus.securitySystemArmed,
                running : results?.deviceStatus.remoteStarterActive,
                latitude: results?.latitude,
                longitude: results?.longitude
            ]
            log.debug "RemoteStart - Locked Status $statusdata.locked"
            log.debug "RemoteStart - Start Status $statusdata.running"
            log.debug "RemoteStart - LatLong $statusdata.latitude / $statusdata.longitude"
            log.debug "RemoteStart - HEADERS: $data.deviceNetworkId"

            def devices = getChildDevices()
            devices.findAll({it.deviceNetworkId == data.deviceNetworkId}).each {
            	it.parseEventData(statusdata)
            }
            return statusdata
        } else if (results.find {it.toString().contains('action')} as Boolean) {
        	//No action needed, Device will send refresh cmd to update itsself
        } else {
        	log.warn "RemoteStart - Did not get expected results from response. Body: $response.data"
        }
    
}

}

def pollStatus(id) {
//Check if token needed
Date latestdate = new Date(); 
if(state.tokenexpiry - latestdate.getTime() <0)
    {
    log.debug "MS left on token: ${state.tokenexpiry - latestdate.getTime()}"
            //Need new SmartStartToken
        GetToken()
    }
    
    def statusuri = "${apiEndpoint}/v1/devices/command"
    def pollParams = [
        method: 'POST',
        uri: statusuri,
        headers: ["Content-Type": "application/json", "Authorization": "Bearer ${state.token}"],
        body: ["deviceId": id, "command": "read_current"]
    ]

    //Data to help the resposne function know what was being asked at the time, and from who (what child device)
    def data = ["deviceNetworkId": id, "request": "readStatus"]
    
    asynchttp_v1.post('postWithJsonHandler', pollParams, data)
}


def sendCommand(cmd, id) {
//Check if token needed
Date latestdate = new Date(); 
if(state.tokenexpiry - latestdate.getTime() <0)
    {
    log.debug "MS left on token: ${state.tokenexpiry - latestdate.getTime()}"
            //Need new SmartStartToken
        GetToken()
    }
    
    def cmdString
    switch (cmd) {
        case "Lock":
        	cmdString = 'arm'
        	break
        case "Unlock":
        	cmdString = 'disarm'
        	break
        case "Start":
        	cmdString = 'remote'
        	break
        default:
        	return
    }
    def cmduri = "${apiEndpoint}/v1/devices/command"
    def params = [
        method: 'POST',
        uri: cmduri,
        headers: ["Content-Type": "application/json", "Authorization": "Bearer ${state.token}"],
        body: ["deviceId": id, "command": cmdString]
    ]
    //Data to help the resposne function know what was being asked at the time, and from who (what child device)
    def data = ["deviceNetworkId": deviceID, "request": "cmd", "cmd": cmd]
    asynchttp_v1.post('postWithJsonHandler', params, data)
}
