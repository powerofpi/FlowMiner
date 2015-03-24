package edu.iastate.flowminer.log;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import com.ensoftcorp.atlas.core.log.ToolboxLog;

import edu.iastate.flowminer.Activator;


/**
* Centralized logging for Eclipse plugins.
*
*/
public class Log {
	public static final String pluginid = "edu.iastate.flowminer";
	private static ILog log;
	private static ToolboxLog tLog;
	
	static {
		BundleContext context = Activator.getContext();
		if (context != null) {
			Bundle bundle = context.getBundle();
			log = Platform.getLog(bundle);
		} 
		if(log == null){
			tLog = new ToolboxLog(pluginid);
		}
	}
	
	public static void error(String message, Throwable e) {
		log(Status.ERROR, message, e);
	}
	
	public static void warning(String message) {
		warning(message, null);
	}
	
	public static void warning(String message, Throwable e) {
		log(Status.WARNING, message, e);
	}
	
	public static void info(String message) {
		info(message, null);
	}
	
	public static void info(String message, Throwable e) {
		log(Status.INFO, message, e);
	}
	
	public static void log(int severity, String string, Throwable e) {
		string = string + "\n" + "Time: " + System.currentTimeMillis();
		if(log == null){
			tLog.log(severity, string, e);
		}else{
			IStatus status = new Status(severity, pluginid, string, e);
			log.log(status);
		}
	}
}
