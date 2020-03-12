package com.puppetlabs.trapperkeeper.services.webserver.jetty9.utils;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.security.CertificateUtils;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.File;
import java.security.cert.CRL;
import java.util.Collection;
import java.util.function.Consumer;

public class InternalSslContextFactory extends SslContextFactory {

    private static int maxTries = 25;
    private static int sleepInMillisecondsBetweenTries = 100;
    private static Consumer<SslContextFactory> consumer = sslContextFactory
            -> {};

    private static final Logger SERVER_LOG =
            Log.getLogger(InternalSslContextFactory.Server.class);
    private static final Logger CLIENT_LOG =
            Log.getLogger(InternalSslContextFactory.Client.class);

    private static class CRLCollection {

	private SslContextFactory factory;
	private final Logger log;
	private Collection<? extends CRL> _crls;

	CRLCollection(SslContextFactory factory, Logger log) {
	    this.factory = factory;
	    this.log = log;
	}

	Collection<? extends CRL> loadCRL(String crlPath) throws Exception {
	    Collection<? extends CRL> crls;

	    synchronized (factory) {
		if (_crls == null) {
		    crls = CertificateUtils.loadCRL(crlPath);
		} else {
		    crls = _crls;
		}
	    }

	    return crls;
	}

	void reload() throws Exception {
	    synchronized (factory) {
		Exception reloadEx = null;
		int tries = maxTries;
		String crlPath = factory.getCrlPath();

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
				log.warn("Failed ssl context reload after " +
					 maxTries + " tries.  CRL file is: " +
					 crlPath, reloadEx);
			    } else {
				Thread.sleep(sleepInMillisecondsBetweenTries);
			    }
			}
		    } while (reloadEx != null && tries > 0);
		}

		if (reloadEx == null) {
		    factory.reload(consumer);
		}
	    }
	}
    }

    public static class Server extends SslContextFactory.Server {
	private CRLCollection _crls;

	public Server() {
	    this._crls = new CRLCollection(this, SERVER_LOG);
	}

	@Override
	protected Collection<? extends CRL> loadCRL(String crlPath) throws Exception {
	    return _crls.loadCRL(crlPath);
	}

	public void reload() throws Exception {
	    _crls.reload();
	}
    }

    public static class Client extends SslContextFactory.Client {
	private CRLCollection _crls;

	public Client() {
	    this._crls = new CRLCollection(this, CLIENT_LOG);
	}

	@Override
	protected Collection<? extends CRL> loadCRL(String crlPath) throws Exception {
	    return _crls.loadCRL(crlPath);
	}

	public void reload() throws Exception {
	    _crls.reload();
	}
    }
}
