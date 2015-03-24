package edu.iastate.flowminer.miner;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

import com.ensoftcorp.atlas.core.script.UniverseManipulator;

import edu.iastate.flowminer.exception.FlowMinerException;
import edu.iastate.flowminer.log.Log;
import edu.iastate.flowminer.schema.SummarySchema;

public abstract class Miner {
	/**
	 * Mine the given summary schema , inserting the resulting
	 * summaries by modifying the current index. To save these summaries for later use,
	 * be sure to export them using Exporter.
	 * 
	 * ASSERT: Library has been indexed
	 * 
	 * @param summaries
	 * @throws Throwable 
	 */
	public static final void mineSummary(final SummarySchema schema) throws Throwable {
		final String schemaName = schema.getClass().getSimpleName();
		
		Job summaryJob = new Job("Mining API Summary: " + schemaName){
			@Override
			protected IStatus run(final IProgressMonitor monitor) {
				SubMonitor sm = SubMonitor.convert(monitor, 285098);
				try{
					String msg = "Mining sumary " + schemaName;
					Log.info(msg);
					
					if(sm.isCanceled()) return Status.CANCEL_STATUS;
					final UniverseManipulator um = new UniverseManipulator();
					Miner miner = schema.getMiner();
					msg = "Mining " + schemaName + " with " + miner;
					Log.info(msg);
					sm.setTaskName(msg);
					miner.mineSummary(sm.newChild(202261), um);
					
					if(sm.isCanceled()) return Status.CANCEL_STATUS;
					msg = "Writing mined summaries to index";
					sm.setTaskName(msg);
					Log.info(msg);
					um.perform(sm.newChild(53745));
					
					msg = "Mining finished";
					Log.info(msg);
					return Status.OK_STATUS;
				}catch(Throwable t){
					return new Status(Status.ERROR, Log.pluginid, "Exception thrown during mining", t);
				}finally{
					sm.done();
				}
			}
		};
		summaryJob.setPriority(Job.LONG);
		summaryJob.schedule();
		summaryJob.join();
	}
	
	/**
	 * Mine the requested API summary, instructing the UM to insert edges.
	 * 
	 * @param um
	 * @param schema
	 * @param apiSurface
	 * @throws Throwable 
	 */
	protected abstract void mineSummary(IProgressMonitor mon, UniverseManipulator um) throws FlowMinerException, Throwable;
}
