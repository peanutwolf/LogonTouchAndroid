package com.ingenico.logontouch.dict

import android.graphics.drawable.Drawable

/**
* Copyright (c) 2018 All Rights Reserved, Ingenico LLC.
*
* Created by vigursky
* Date: 19.01.2018
*/

enum class StatusState(val state: String) {
    APPLICATION_LOADING("Initializing application resources"),

    HOST_UNLOCKED("PC successfully unlocked..."),
    HOST_NOT_UNLOCKED("PC unlock failed. Something went wrong"),

    DEVICE_BIND("Device bound to PC\n" +
            "Now you can unlock your PC remotely"),
    DEVICE_NOT_BIND("Device isn't bound to PC.\n" +
            "Please launch LogonTouch on PC \n" +
            "and click 'BIND DEVICE' button"),

    KEYGUARD_UNSECURED(""),

    USER_CANCELLED("User cancelled request"),
    NO_CLIENT_CERT("Client certificates not generated\n" +
            "Please generate QR code with LogonTouch PC software\n" +
            "and try again"),
    HOST_UNREACHABLE("LogonTouch PC Server is unreachable\n" +
            "Please check if WiFi is turned on\n" +
            "And phone can connect to PC through network"),
    CONNECTION_TIMEOUT("LogonTouch PC connection timeout"),
    GENERAL_ERROR("Error occurred")
}