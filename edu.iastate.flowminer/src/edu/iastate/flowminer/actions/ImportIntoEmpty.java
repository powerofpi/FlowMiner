package edu.iastate.flowminer.actions;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.index.Index;

import edu.iastate.flowminer.importer.Importer;
import edu.iastate.flowminer.log.Log;
import edu.iastate.flowminer.schema.ISUSchema;

public class ImportIntoEmpty extends FlowMinerAction {
	/**
	 * The action has been activated. The argument of the
	 * method represents the 'real' action sitting
	 * in the workbench UI.
	 * @see IWorkbenchWindowActionDelegate#run
	 */
	public void run(IAction action) {
		// Get a save path from the user
		final String chosenFile = chooseSummaryFile("Select summary file to load...");
		// User canceled
		if(chosenFile == null) return;
		
		doImport(chosenFile, false);
	}
	
	public void doImport(final String path, boolean block){
		Job job = new Job("FlowMiner Summary Import"){
			@Override
			protected IStatus run(final IProgressMonitor mon) {
				try{
					// Clear the current index.
					Graph.U.clear(); Index.getInstance().clear(); Index.getInstance().ensureIndex();
					Importer.importSummary(ISUSchema.INSTANCE, false, path);
				}catch(Throwable t){
					return new Status(IStatus.ERROR, Log.pluginid, "FlowMiner encountered a problem during summary import", t);
				}
				return new Status(IStatus.OK, Log.pluginid, null);
			}
		};
		job.setPriority(Job.LONG);
		job.schedule();
		if(block){
			try {
				job.join();
			} catch (InterruptedException e) {}
		}
	}
}