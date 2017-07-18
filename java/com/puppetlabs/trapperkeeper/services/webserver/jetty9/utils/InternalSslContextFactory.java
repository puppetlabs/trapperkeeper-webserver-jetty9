package com.puppetlabs.trapperkeeper.services.webserver.jetty9.utils;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.security.CertificateUtils;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.cert.CRL;
import java.util.Collection;

public class InternalSslContextFactory extends SslContextFactory {

    private static int maxTries = 25;
    private static int sleepInMillisecondsBetweenTries = 100;
    private static final Logger LOG =
            Log.getLogger(InternalSslContextFactory.class);

    private Collection<? extends CRL> _crls;

    @Override
    protected void checkNotStarted() {
    }

    @Override
    protected void doStart() throws Exception {
        synchronized (this) {
            load();
        }
    }

    @Override
    protected void doStop() throws Exception {
        synchronized (this) {
            unload();
        }
        super.doStop();
    }

    @Override
    protected Collection<? extends CRL> loadCRL(String crlPath) throws Exception {
        Collection<? extends CRL> crls;

        synchronized (this) {
            if (_crls == null) {
                crls = super.loadCRL(crlPath);
            } else {
                crls = _crls;
            }
        }

        return crls;
    }

    private void load() throws Exception {
        synchronized (this) {
            super.doStart();
        }
    }

    private void unload() throws Exception {
        synchronized (this) {
            super.doStop();
        }
    }

    @Override
    public SSLContext getSslContext() {
        synchronized (this) {
            return super.getSslContext();
        }
    }

    @Override
    public SSLServerSocket newSslServerSocket(String host,
                                              int port,
                                              int backlog)
            throws IOException {
        synchronized (this) {
            return super.newSslServerSocket(host, port, backlog);
        }
    }

    @Override
    public SSLSocket newSslSocket() throws IOException {
        synchronized (this) {
            return super.newSslSocket();
        }
    }

    @Override
    public SSLEngine newSSLEngine() {
        synchronized (this) {
            return super.newSSLEngine();
        }
    }

    @Override
    public SSLEngine newSSLEngine(String host, int port) {
        synchronized (this) {
            return super.newSSLEngine(host, port);
        }
    }

    @Override
    public SSLEngine newSSLEngine(InetSocketAddress address) {
        synchronized (this) {
            return super.newSSLEngine(address);
        }
    }

    public void reload() throws Exception {
        synchronized (this) {
            Exception reloadEx = null;
            int tries = maxTries;
            String crlPath = getCrlPath();

            if (crlPath != null) {
                File crlPathAsFile = new File(crlPath);
                long crlLastModified = crlPathAsFile.lastModified();

                // Try to parse CRLs from the crlPath until it is successful
                // or a hard-coded number of failed attempts have been made.
                do {
                    reloadEx = null;
                    try {
                        _crls = CertificateUtils.loadCRL(crlPath);
                    } catch (Exception e) {
                        reloadEx = e;

                        // If the CRL file has been updated since the last reload
                        // attempt, reset the retry counter.
                        if (crlPathAsFile != null &&
                                crlLastModified != crlPathAsFile.lastModified()) {
                            crlLastModified = crlPathAsFile.lastModified();
                            tries = maxTries;
                        } else {
                            tries--;
                        }

                        if (tries == 0) {
                            LOG.warn("Failed ssl context reload after " +
                                    maxTries + " tries.  CRL file is: " +
                                    crlPath, reloadEx);
                        } else {
                            Thread.sleep(sleepInMillisecondsBetweenTries);
                        }
                    }
                } while (reloadEx != null && tries > 0);
            }

            if (reloadEx == null) {
                unload();
                load();
            }
        }
    }
}
