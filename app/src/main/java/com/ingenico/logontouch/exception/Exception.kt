package com.ingenico.logontouch.exception

/**
 * Created by vigursky on 19.01.2018.
 */

class UserCancelledException: Throwable("User cancelled request")

class CertificateNotReadyException: Throwable("Certificate not Ready")

class DeviceNotBindException: Throwable("Device Not Bound to secure host")
