package com.puppetlabs.trapperkeeper.services.webserver.jetty9.utils;

import ch.qos.logback.access.jetty.RequestLogImpl;
import org.eclipse.jetty.util.component.LifeCycle;

/*
	Sit down, it's story time.
	Once upon a time logback had a RequestLogImpl that you could just drop in
	Jetty and use without any modifications. Developers used this and it was
	good. Then, during Jetty 9.3 development, the RequestLog interface that
	RequestLogImpl implemented was "refactored"[0] to no longer extend Jetty's
	LifeCycle interface (this is distinct from Logback's LifeCycle interface,
	so try to keep up).
	Implementing Jetty's LifeCycle interface turns out to be important because
	Jetty uses it to decide whether or not to automatically start a Bean[1].
	Many people were sad about this[2] and tried to make the RequestLogImpl
	again start automatically with Jetty[3], but their efforts have so far not
	been merged.
	In order for our RequestLogImpl to automatically start, we decide to extend
	the existing built-in logback implementation and have it implement Jetty's
	LifeCycle interface, which it already does, but doesn't declare.

	[0] - https://github.com/eclipse/jetty.project/commit/e3bda4ef
	[1] - https://github.com/eclipse/jetty.project/blob/0c8273f2ca1f9bf2064cd9c4c939d2546443f759/jetty-util/src/main/java/org/eclipse/jetty/util/component/ContainerLifeCycle.java#L98
	[2] - https://jira.qos.ch/browse/LOGBACK-1052
	[3] - https://github.com/qos-ch/logback/pull/269

	And Jetty and Logback lived happily ever after.

 */

public class LifeCycleImplementingRequestLogImpl extends RequestLogImpl implements LifeCycle {}
