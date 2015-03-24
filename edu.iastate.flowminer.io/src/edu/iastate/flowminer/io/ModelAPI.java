package edu.iastate.flowminer.io;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.zip.GZIPOutputStream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.log.Log;
import edu.iastate.flowminer.io.model.IOModel;

public class ModelAPI {
	public static final String JAXP_SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";
	public static final String SCHEMA_SOURCE = "edu.iastate.flowminer.io.xsd";
	
	public static void exportToFile(final IProgressMonitor mon, final IOModel model, final String destination)
			throws Throwable {
		final Throwable[] error = new Throwable[1];

		class JobExporter{
			IProgressMonitor monitor;
			public JobExporter(IProgressMonitor monitor){
				this.monitor = monitor;
			}
			
			IStatus doExport(){
				try{
					// Conversion
					StringBuilder sb = new StringBuilder(1000000000);
					sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
					model.convert(sb, Schema.IOMODEL);

					// Output
					BufferedWriter wr = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(destination), 65536), Charset.forName("UTF-8")), 65536);
					wr.write(sb.toString());
					wr.flush();
					wr.close();
					
					return Status.OK_STATUS;
				}catch(Throwable t){
					error[0] = t;
					Log.error("IO export problem", error[0]);
					return new Status(Status.ERROR, Log.pluginid, "Exception thrown during import", t);
				}
				finally{
					monitor.done();
				}
			}
		}
		
		if(mon == null){
			Job job = new Job("FlowMiner exporting summaries to file."){
				@Override
				protected IStatus run(final IProgressMonitor monitor) {
					return new JobExporter(monitor).doExport();
				}
			};
			job.setPriority(Job.LONG);
			job.schedule();
			job.join();
		} else{
			if(mon.isCanceled()) return;
			try{
				new JobExporter(mon).doExport();
			}finally{
				mon.done();
			}
		}
		
		if(error[0] != null) throw error[0];
	}
	
	public static IOModel importFromFile(final IProgressMonitor mon, final String source) throws Throwable {
		final IOModel[] result = new IOModel[1];
		final Throwable[] error = new Throwable[1];

		class JobImporter{
			IProgressMonitor monitor;
			public JobImporter(IProgressMonitor monitor){
				this.monitor = monitor;
			}
			
			IStatus doImport(){
				try{
					// Setup
					VTDGen vg = new VTDGen();
					vg.parseGZIPFile(source, false);
					VTDNav vn = vg.getNav();
					
					// Converted Output
					result[0] = new IOModel(monitor, vn);
					return Status.OK_STATUS;
				}catch(Throwable t){
					error[0] = t;
					Log.error("IO import problem", error[0]);
					return new Status(Status.ERROR, Log.pluginid, "Exception thrown during import", t);
				}
				finally{
					monitor.done();
				}
			}
		}
		
		if(mon == null){
			Job job = new Job("FlowMiner importing summaries from file"){
				@Override
				protected IStatus run(final IProgressMonitor monitor) {
					return new JobImporter(monitor).doImport();
				}
			};
			job.setPriority(Job.LONG);
			job.schedule();
			job.join();
		} else{
			if(mon.isCanceled()) return null;
			try{
				new JobImporter(mon).doImport();
			}finally{
				mon.done();
			}
		}
		
		if(error[0] != null) throw error[0];
		return result[0];
	}
}