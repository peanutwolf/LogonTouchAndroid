package com.ingenico.logontouch.dict

/**
 * Created by vigursky on 19.01.2018.
 */
enum class StatusState(val state: String) {
    APPLICATION_LOADING("Initializing application resources"),

    DEVICE_BIND("Device successfully bind to host"),
    DEVICE_NOT_BIND("Device need to be bind to host"),

    KEYGUARD_UNSECURED(""),

    USER_CANCELLED("User cancelled request"),
    NO_CLIENT_CERT("Client certificates to generated"),
    HOST_UNREACHABLE("LogonTouch Server unreachable"),
    CONNECTION_TIMEOUT("LogonTouch Server connection timeout"),
    GENERAL_ERROR("Error occurred")
}