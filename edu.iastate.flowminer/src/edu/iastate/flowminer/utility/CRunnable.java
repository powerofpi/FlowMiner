package edu.iastate.flowminer.utility;

import org.eclipse.core.runtime.IProgressMonitor;

public class CRunnable implements Runnable{
	IProgressMonitor mon;
	Runnable r;
	
	public CRunnable(IProgressMonitor mon, Runnable r){
		this.mon = mon;
		this.r = r;
	}
	
	@Override
	public void run() {
		if(mon != null && mon.isCanceled()) return;
		r.run();
		if(mon != null){
			synchronized(mon){
				mon.worked(1);
			}
		}
	}
}