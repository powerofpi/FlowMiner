package edu.iastate.flowminer.actions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import net.ontopia.utils.CompactHashMap;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

import com.ensoftcorp.abp.core.conversion.EclipseUtil;
import com.ensoftcorp.abp.core.conversion.JarToJimple;
import com.ensoftcorp.atlas.core.indexing.IMappingSettings;
import com.ensoftcorp.atlas.core.indexing.IndexingUtil;
import com.ensoftcorp.atlas.java.core.settings.JavaIndexingSettings;
import com.ensoftcorp.atlas.java.core.settings.OptionIndexJars;

import edu.iastate.flowminer.exporter.Exporter;
import edu.iastate.flowminer.log.Log;
import edu.iastate.flowminer.miner.Miner;
import edu.iastate.flowminer.schema.ISUSchema;

public class MineAndExport extends FlowMinerAction {
	/**
	 * The action has been activated. The argument of the
	 * method represents the 'real' action sitting
	 * in the workbench UI.
	 * @see IWorkbenchWindowActionDelegate#run
	 */
	public void run(IAction action) {
		/*
		 * Get user input
		 */
		final String[] toSummarize = chooseJARs("Select library JAR file(s) to summarize...");
		if(toSummarize.length == 0) return;
		
		final String[] dependencies = chooseJARs("Select any dependency JAR files, or 'cancel' if none...");
		
		final IVMInstall vm = chooseVM("Select a JRE...");
		
		final String summaryDestination = chooseSummaryFile("Select destination for FlowMiner summaries...");
		if(summaryDestination == null) return;
	
		doMineAndExport(toSummarize, dependencies, summaryDestination, vm, false);
	}
	
	void doMineAndExport(final String[] toSummarize, final String[] dependencies, final String summaryDestination, boolean block){
		doMineAndExport(toSummarize, dependencies, summaryDestination, JavaRuntime.getDefaultVMInstall(), block);
	}
	
	public void doMineAndExport(final String[] toSummarize, final String[] dependencies, final String summaryDestination, final IVMInstall vm, boolean block){
		Job job = new Job("FlowMiner Summary Mine and Export "){
			@Override
			protected IStatus run(final IProgressMonitor mon) {
				/*
				 * Create the temporary Java project
				 */
				SubMonitor sm = SubMonitor.convert(mon, 42161);
				IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
				IProject project = root.getProject("FlowMiner_" + System.currentTimeMillis());
				try{
					String msg = "Creating temporary Eclipse project with selected JARs";
					Log.info(msg);
					sm.setTaskName(msg);
					
					String summarizing = Arrays.asList(toSummarize).stream().collect(Collectors.joining("; "));
					String deps = Arrays.asList(dependencies).stream().collect(Collectors.joining("; "));
					Log.info("\nSummarizing: " + summarizing + "\nDependencies: " + deps + "\nVM: " + vm.getName());
					
					project.create(null);
					project.open(null);
					IProjectDescription description = project.getDescription();
					description.setNatureIds(new String[] { JavaCore.NATURE_ID });
					project.setDescription(description, null);
					IJavaProject javaProject = JavaCore.create(project); 
			
					if(sm.isCanceled()) return new Status(IStatus.CANCEL, Log.pluginid, null);
					IFolder binFolder = project.getFolder("bin");
					binFolder.create(false, true, null);
					javaProject.setOutputLocation(binFolder.getFullPath(), null);
					
					if(sm.isCanceled()) return new Status(IStatus.CANCEL, Log.pluginid, null);
					IFolder jimpleFolder = project.getFolder("jimple");
					jimpleFolder.create(false, true, null);
					
					// Compute new class path: (jars to summarize, dependency jars, old entries, system entries)
					LibraryLocation[] vmLibLocations = vm == null ? new LibraryLocation[0]:JavaRuntime.getLibraryLocations(vm);
					IClasspathEntry[] newEntries = new IClasspathEntry[toSummarize.length + dependencies.length + vmLibLocations.length];
					for(int i = 0; i < toSummarize.length; ++i)
						newEntries[i] = JavaCore.newLibraryEntry(new org.eclipse.core.runtime.Path(toSummarize[i]), null, null, false);
					for(int i = 0; i < dependencies.length; ++i)
						newEntries[toSummarize.length + i] = JavaCore.newLibraryEntry(new org.eclipse.core.runtime.Path(dependencies[i]), null, null, false);
					for(int i = 0; i < vmLibLocations.length; ++i)
						newEntries[toSummarize.length + dependencies.length + i] = JavaCore.newLibraryEntry(vmLibLocations[i].getSystemLibraryPath(), null, null, false);
					javaProject.setRawClasspath(newEntries, null);
					sm.worked(3037);
					
					/*
					 * Create Jimple
					 */
					msg = "Generating Jimple for JARs";
					Log.info(msg);
					sm.setTaskName(msg);
					String jimpleDir = jimpleFolder.getLocation().toString();
					JarToJimple.setPhantomRefsOverride(Boolean.TRUE);
					String classpath = EclipseUtil.getAbsoluteClasspath(project);
					for(String jarPath : toSummarize){
						if(sm.isCanceled()) return new Status(IStatus.CANCEL, Log.pluginid, null);
						JarToJimple.jarToJimple(jarPath, jimpleDir, classpath);
					}
					if(sm.isCanceled()) return new Status(IStatus.CANCEL, Log.pluginid, null);
					// Refresh project so that Jimple files are visible to Atlas
					project.refreshLocal(Integer.MAX_VALUE, null);
					sm.worked(4233);
					
					/*
					 * Index with Atlas
					 */
					if(sm.isCanceled()) return new Status(IStatus.CANCEL, Log.pluginid, null);
					msg = "Indexing Jimple with Atlas";
					Log.info(msg);
					sm.setTaskName(msg);
					Collection<IMappingSettings> indexingSettings = new ArrayList<IMappingSettings>(1);
					indexingSettings.add(new JavaIndexingSettings(OptionIndexJars.INDEX_RELEVANT));
					IndexingUtil.indexWithSettings(false, indexingSettings, project);
					sm.worked(23828);
					
					/*
					 * Mine
					 */
					if(sm.isCanceled()) return new Status(IStatus.CANCEL, Log.pluginid, null);
					msg = "Mining summaries";
					Log.info(msg);
					sm.setTaskName(msg);
					Miner.mineSummary(ISUSchema.INSTANCE);
					sm.worked(2984);
					
					/*
					 * Export
					 */
					if(sm.isCanceled()) return new Status(IStatus.CANCEL, Log.pluginid, null);
					msg = "Exporting summaries";
					Log.info(msg);
					sm.setTaskName(msg);
					Exporter.exportSummary(ISUSchema.INSTANCE, summaryDestination);
					sm.worked(8079);
					
					msg = "FlowMiner finished summarizing requested libraries";
					Log.info(msg);
					sm.setTaskName(msg);
				} catch (Throwable t) {
					return new Status(IStatus.ERROR, Log.pluginid, "FlowMiner encountered an error", t);
				}finally{
					// Delete the temporary project
					try {
						project.delete(true, null);
					} catch (CoreException e) {
						Log.warning("Unable to delete temporary project", e);
					}
				}
				return new Status(IStatus.OK, Log.pluginid, null);
			}
		};
		job.setPriority(Job.LONG);
		job.schedule();
		if(block){
			try {
				job.join();
				
				// propagate errors up
				IStatus result = job.getResult();
				if (result.getSeverity() != IStatus.OK)
					throw new RuntimeException(result.getException());
				
			} catch (InterruptedException e) {}
		}
	}
	
	private IVMInstall chooseVM(String msg){
		// Enumerate all JREs that Eclipse knows about
		Map<String, IVMInstall> vms = new CompactHashMap<String, IVMInstall>();
		for(IExecutionEnvironment environment : JavaRuntime.getExecutionEnvironmentsManager().getExecutionEnvironments())
			addJREsForEnvironment(vms, environment);

		// Ask the user to select one
		ElementListSelectionDialog dialog = new ElementListSelectionDialog(window.getShell(), new LabelProvider());
		String[] labels = new String[vms.size() + 1];
		int idx = 0;
		for(String name : vms.keySet()) 
			labels[idx++] = name;
		labels[idx] = "No JRE";
		dialog.setElements(labels);
		dialog.setTitle(msg);
		dialog.setAllowDuplicates(false);
		dialog.setMultipleSelection(false);
		
		// Return the selected JRE
		if (dialog.open() != Window.OK) 
		    return null;
		return vms.get(dialog.getFirstResult());
	}
	
	private void addJREsForEnvironment(Map<String, IVMInstall> vms, IExecutionEnvironment environment){
		for(IExecutionEnvironment subenvironment : environment.getSubEnvironments())
			addJREsForEnvironment(vms, subenvironment);
		
		for(IVMInstall vm : environment.getCompatibleVMs())
			vms.put(vm.getName(), vm);
	}
}