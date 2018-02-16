package com.ingenico.logontouch.exception

/**
 * Created by vigursky on 19.01.2018.
 */

class UserCancelledException: Throwable("User cancelled request exception")

class CertificateNotReadyException: Throwable("Certificate not Ready exception")

class DeviceNotBindException: Throwable("Device Not Bind exception")