package edu.iastate.flowminer.exporter;

import static com.ensoftcorp.atlas.core.script.Common.empty;
import static com.ensoftcorp.atlas.core.script.Common.toGraph;
import static com.ensoftcorp.atlas.core.script.Common.toQ;
import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import net.ontopia.utils.CompactHashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

import com.ensoftcorp.atlas.core.db.graph.Address;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.notification.NotificationSet;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.java.core.script.Common;

import edu.iastate.flowminer.io.Schema;
import edu.iastate.flowminer.io.model.Element;
import edu.iastate.flowminer.io.model.IOModel;
import edu.iastate.flowminer.log.Log;
import edu.iastate.flowminer.schema.SummarySchema;

public abstract class Exporter {
	protected static long idGenerator = 0;
	private static Map<Address, Long> addressToID = new CompactHashMap<Address, Long>();
	
	public static Q findNode(String addressString){
		for(GraphElement ge : Graph.U.nodes()){
			if(addressString.equals(ge.address().toAddressString())){
				return toQ(toGraph(ge));
			}
		}
		return empty();
	}
	
	/**
	 * Export the requested summary schema to its compressed XML file.
	 * 
	 * ASSERT: You have already mined the given summary schema using Miner.
	 * 
	 * @param toExport
	 * @param destinationFile
	 * @throws Throwable 
	 */
	public static void exportSummary(final SummarySchema schema, final String path) throws Throwable{
		exportSummary(null, schema, path);
	}
	
	/**
	 * Export the requested summary schema to its compressed XML file.
	 * 
	 * ASSERT: You have already mined the given summary schema using Miner.
	 * 
	 * @param toExport
	 * @param destinationFile
	 * @throws Throwable 
	 */
	public static void exportSummary(final IProgressMonitor mon, final SummarySchema schema, final String path) throws Throwable{
		final String schemaName = schema.getClass().getSimpleName();
		class JobExporter{
			IProgressMonitor monitor;
			public JobExporter(IProgressMonitor monitor){
				this.monitor = monitor;
			}	
			
			IStatus doExport(final SummarySchema schema){
				SubMonitor sm = SubMonitor.convert(monitor, 1170998);
				try{
					String msg = "Exporting API Summary: " + schemaName;
					Log.info(msg);
					
					Exporter atlasExporter = new AtlasExporter();
					
					// Get export contributions
					if(sm.isCanceled()) return Status.CANCEL_STATUS;
					msg = "Asking exporters for contributions";
					Log.info(msg);
					sm.setTaskName(msg);
					Q schemaContribution = schema.getExporter().contributeExports(sm.newChild(1), universe());
					Q atlasContribution = atlasExporter.contributeExports(sm.newChild(609805), schemaContribution);
					
					// Resolve contributions
					if(sm.isCanceled()) return Status.CANCEL_STATUS;
					msg = "Resolving exporter contributions";
					Log.info(msg);
					sm.setTaskName(msg);
					Q toExport = Common.resolve(sm.newChild(123300), schemaContribution.union(atlasContribution));
					
					// Export Atlas portion
					if(sm.isCanceled()) return Status.CANCEL_STATUS;
					msg = "Exporting Atlas portion of contributions";
					Log.info(msg);
					sm.setTaskName(msg);
					IOModel model = new IOModel(Platform.getBundle(Log.pluginid).getVersion().toString(), new Date());
					Graph toExportG = toExport.eval();
					Map<GraphElement, Element> exported = new CompactHashMap<GraphElement, Element>((int) (toExportG.nodes().size() + toExportG.edges().size()));
					atlasExporter.exportSummary(sm.newChild(124486), exported, model, toExport);
					
					// Export schema portion
					if(sm.isCanceled()) return Status.CANCEL_STATUS;
					msg = "Exporting " + schemaName + " portion of contributions";
					Log.info(msg);
					sm.setTaskName(msg);
					schema.getExporter().exportSummary(sm.newChild(211392), exported, model, toExport);

					// Export model to file
					if(sm.isCanceled()) return Status.CANCEL_STATUS;
					msg = "Exporting model to file";
					Log.info(msg);
					sm.setTaskName(msg);
					edu.iastate.flowminer.io.ModelAPI.exportToFile(sm.newChild(74073), model, path);
					
					msg = "Export finished";
					Log.info(msg);
				}catch(Throwable t){
					return new Status(Status.ERROR, Log.pluginid,"Exception thrown during export",t);
				}finally{
					sm.done();
				}
				return Status.OK_STATUS;
			}
		}

		if(mon == null){
			Job job = new Job("Exporting API summary for " + schemaName){
				@Override
				protected IStatus run(final IProgressMonitor monitor) {
					return new JobExporter(monitor).doExport(schema);
				}
			};
			job.setPriority(Job.LONG);
			job.schedule();
			job.join();
		}else{
			if(mon.isCanceled()) return;
			try{
				new JobExporter(mon).doExport(schema);
			}finally{
				mon.done();
			}
		}
	}
	
	protected static Long getIDForAddress(Address a){
		synchronized(addressToID){
			Long id = addressToID.get(a);
			if(id == null){
				id = idGenerator++;
				addressToID.put(a,  id);
			}
			return id;
		}
	}
	
	protected static void addTags(Element e, GraphElement ge){
		Set<String> tags = e.getTag();
		NotificationSet<String> geTags = ge.tags();
		if(geTags.contains(XCSG.publicVisibility)) tags.add(Schema.Tag.IS_PUBLIC);
		if(geTags.contains(XCSG.protectedPackageVisibility)) tags.add(Schema.Tag.IS_PROTECTED);
		if(geTags.contains(XCSG.privateVisibility)) tags.add(Schema.Tag.IS_PRIVATE);
		if(geTags.contains(XCSG.abstractMethod) || geTags.contains(XCSG.Java.AbstractClass)) tags.add(Schema.Tag.IS_ABSTRACT);
		if(geTags.contains(XCSG.immutable) || geTags.contains(XCSG.Java.finalClass)) tags.add(Schema.Tag.IS_FINAL);
		if(geTags.contains(XCSG.Java.nativeMethod)) tags.add(Schema.Tag.IS_NATIVE);
		if(geTags.contains(XCSG.ClassMethod) || geTags.contains(XCSG.ClassVariable)) tags.add(Schema.Tag.IS_STATIC);
		if(geTags.contains(XCSG.Java.synchronizedMethod)) tags.add(Schema.Tag.IS_SYNCHRONIZED);
		if(geTags.contains(Attr.Node.IS_STRICTFP)) tags.add(Schema.Tag.IS_STRICTFP);
		if(geTags.contains(XCSG.Java.transientField)) tags.add(Schema.Tag.IS_TRANSIENT);
		if(geTags.contains(XCSG.volatileVariable)) tags.add(Schema.Tag.IS_VOLATILE);
	}
	
	/**
	 * Add this summary framework's contribution to the given model.
	 * 
	 * @param mon
	 * @param model
	 * @param toExport
	 * @param libSurface
	 */
	protected abstract void exportSummary(IProgressMonitor mon, Map<GraphElement, Element> exported, IOModel model, Q toExport) throws Throwable;
	
	/**
	 * Communicate what this exporter intends to export.
	 * 
	 * @param mon
	 * @param libSurface
	 * @return
	 */
	protected abstract Q contributeExports(IProgressMonitor mon, Q contributeFor) throws Throwable;
}