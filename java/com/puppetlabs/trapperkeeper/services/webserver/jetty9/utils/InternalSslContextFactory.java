package com.puppetlabs.trapperkeeper.services.webserver.jetty9.utils;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.security.CertificateUtils;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.File;
import java.security.cert.CRL;
import java.util.Collection;
import java.util.function.Consumer;

public class InternalSslContextFactory extends SslContextFactory.Server {

    private static int maxTries = 25;
    private static int sleepInMillisecondsBetweenTries = 100;
    private static final Logger LOG =
            Log.getLogger(InternalSslContextFactory.class);
    private static Consumer<SslContextFactory> consumer = sslContextFactory
            -> {};

    private Collection<? extends CRL> _crls;

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
                reload(consumer);
            }
        }
    }
}
