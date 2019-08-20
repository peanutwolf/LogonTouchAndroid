package com.ingenico.logontouch.test;

import android.content.Context;

import com.ingenico.logontouch.R;
import com.ingenico.logontouch.tools.NoSSLv3Factory;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import androidx.annotation.RawRes;
import okhttp3.OkHttpClient;

/**
 * Created by vigursky on 09.11.2017.
 */

public class TestJava {

    public static  OkHttpClient.Builder getUnsafeOkHttpClientBuiler() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {

                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {

                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {

                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder okHttpClient = new OkHttpClient.Builder().sslSocketFactory(sslSocketFactory).hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

            return okHttpClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static KeyStore readKeyStore(Context context) {
        KeyStore ks = null;
        try {
            ks = KeyStore.getInstance("PKCS12");
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }

        // get user password and file input stream
        char[] password = "androidclient".toCharArray();

        java.io.InputStream fis = null;
        try {
            fis = context.getResources().openRawResource(R.raw.keystore);
            ks.load(fis, password);
        }catch (FileNotFoundException ex){
            ex.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ks;
    }

    static KeyStore readKeyStore(Context context, @RawRes int id, String pass) {
        KeyStore ks = null;
        try {
            ks = KeyStore.getInstance("PKCS12");
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }

        // get user password and file input stream
        char[] password = pass.toCharArray();

        java.io.InputStream fis = null;
        try {
            fis = context.getResources().openRawResource(id);
            ks.load(fis, password);
        }catch (FileNotFoundException ex){
            ex.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ks;
    }

    static public  TrustManager[] getPEMTrustManager(Context context){
        KeyStore keys = null;
        TrustManager[] trustManagers = null;
        try {
            keys = KeyStore.getInstance(KeyStore.getDefaultType());
            keys.load(null, null);
            java.io.InputStream certInputStream = context.getResources().openRawResource(R.raw.android);
            BufferedInputStream bis = new BufferedInputStream(certInputStream);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            while (bis.available() > 0) {
                Certificate cert = certificateFactory.generateCertificate(bis);
                keys.setCertificateEntry("www.example.com", cert);
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keys);
            trustManagers = trustManagerFactory.getTrustManagers();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return trustManagers;

    }

    static public OkHttpClient.Builder getSafeOnlyPEMOkHttpBuilder(Context context){
        KeyStore keys = null;
        SSLContext sslContext = null;
        TrustManager[] trustManagers = null;
        try {
            keys = KeyStore.getInstance(KeyStore.getDefaultType());
            keys.load(null, null);
            java.io.InputStream certInputStream = context.getResources().openRawResource(R.raw.android);
            BufferedInputStream bis = new BufferedInputStream(certInputStream);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            while (bis.available() > 0) {
                Certificate cert = certificateFactory.generateCertificate(bis);
                keys.setCertificateEntry("www.example.com", cert);
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keys);
            trustManagers = trustManagerFactory.getTrustManagers();
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }

        OkHttpClient.Builder client = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]).hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });

        return client;
    }

    static public OkHttpClient.Builder getSafeOkHttpBuilder(Context context){
        KeyStore serverkeystore = readKeyStore(context, R.raw.server, "0nf0CELqjuYRRt825WHX");
        KeyStore clientkeystore = readKeyStore(context, R.raw.client, "keypass.key");
        KeyManagerFactory keyManagerFactory = null;
        TrustManagerFactory trustManagerFactory = null;
        SSLContext sslContext = null;
        try {
            keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(clientkeystore, "keypass.key".toCharArray());
            trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(serverkeystore);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        }


        try {
            sslContext = SSLContext.getInstance("TLSv1.2");

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        try {
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        NoSSLv3Factory noSSLv3Factory = new NoSSLv3Factory(sslContext.getSocketFactory());
        OkHttpClient.Builder okHttpClient = new OkHttpClient.Builder().sslSocketFactory(noSSLv3Factory).hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });

         return okHttpClient;
    }

}
