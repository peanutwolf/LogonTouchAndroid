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
            "Please launch LogonTouchUI on PC \n" +
            "and click 'GenerateQR' button"),

    KEYGUARD_UNSECURED(""),

    USER_CANCELLED("User cancelled request"),
    NO_CLIENT_CERT("Client certificates not generated\n" +
            "Please generate QR code with LogonTouchUI software"),
    HOST_UNREACHABLE("PC is unreachable. Please check:\n" +
            "- WiFi is connected to PC's network.\n" +
            "- Device has been correctly bound to PC.\n"),
    CONNECTION_TIMEOUT("Connection timeout..."),
    GENERAL_ERROR("Error occurred")
}