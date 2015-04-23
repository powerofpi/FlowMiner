package edu.iastate.flowminer.actions;

import static com.ensoftcorp.atlas.core.script.Common.index;
import static com.ensoftcorp.atlas.core.script.Common.toGraph;
import static com.ensoftcorp.atlas.core.script.Common.toQ;
import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import edu.iastate.flowminer.log.Log;
import edu.iastate.flowminer.schema.ISUSchema;

public class Experiment extends FlowMinerAction {
	private static final String PARSE_CHAR = ":";
	
	/**
	 * The action has been activated. The argument of the
	 * method represents the 'real' action sitting
	 * in the workbench UI.
	 * @see IWorkbenchWindowActionDelegate#run
	 */
	public void run(IAction action) {
		// Get a save path from the user
		final String configFile = chooseFile("Choose experiment configuration file...", "CSV",".csv");
		if(configFile == null) return;
		
		final String outputFile = chooseFile("Choose experiment output file...", "CSV",".csv");
		if(outputFile == null) return;

		Job job = new Job("FlowMiner Summary Experiment"){
			@Override
			protected IStatus run(final IProgressMonitor mon) {
				try{
					// Ensure that an index exists
					experiment(configFile, outputFile);
				}catch(Throwable t){
					return new Status(IStatus.ERROR, Log.pluginid, "FlowMiner encountered a problem during experiment", t);
				}
				return new Status(IStatus.OK, Log.pluginid, null);
			}
		};
		job.setPriority(Job.LONG);
		job.schedule();
	}
	
	/**
	 * Runs the FlowMiner paper experiment from the content in the given configuration CSV file. Expected format:
	 * 
	 * jar1:jar2:..., dependency1:dependency2:..., path/to/summary1.xml.gz
	 * jar3:jar4:..., dependency1:dependency2:..., path/to/summary2.xml.gz
	 * ...
	 * 
	 * @param configFile
	 * @return
	 * @throws ExecutionException
	 * @throws NotDefinedException
	 * @throws NotEnabledException
	 * @throws NotHandledException
	 * @throws IOException
	 */
	public static void experiment(String configuration, String output) throws ExecutionException, NotDefinedException, NotEnabledException, NotHandledException, IOException{
		// Parse the configuration
		CSVReader reader = new CSVReader(new FileReader(configuration));
	    List<String[]> myEntries = reader.readAll();
	    reader.close();
		
	    CSVWriter writer = new CSVWriter(new FileWriter(output), '\t');
		writer.writeNext(new String[]{"Summarized JARs", "Dependencies", "Summary Output", "G(P) Nodes", "G(P) Edges", "G'(P) Nodes", "G'(P) Edges", "Field Granularity Flows", "Object Granularity Flows"});
	    
		for(String[] entry : myEntries){
			try{
				Log.info("Running experiment on " + Arrays.toString(entry));
				String[] jars = entry[0].split(PARSE_CHAR);
				String[] dependencies = entry[1].split(PARSE_CHAR);
				if(dependencies[0].length()==0) dependencies = new String[0];
				String summaryOutput = entry[2];
				
				// Mine summaries and export them
				new MineAndExport().doMineAndExport(jars, dependencies, summaryOutput, true);
				
				Q i = index();
				int nodes = (int) i.eval().nodes().size();
				int edges = (int) i.eval().edges().size();
				
				// Import summaries into an empty index
				new ImportIntoEmpty().doImport(summaryOutput, true);
				i = universe();
				int sNodes = (int) i.eval().nodes().size();
				int sEdges = (int) i.eval().edges().size();
				
				Q ipDF = i.edgesTaggedWithAny(ISUSchema.Edge.FLOW_FIELD);
				Q cont = i.edgesTaggedWithAny(XCSG.Contains);
				
				int fFlows = 0;
				int oFlows = 0;
				for(GraphElement type : Graph.U.nodes().taggedWithAny(XCSG.Type)){
					Q typeQ = toQ(toGraph(type));
					Q fields = cont.successors(typeQ).nodesTaggedWithAny(XCSG.Field);
					
					oFlows += ipDF.reverseStep(fields).eval().edges().size() * ipDF.forwardStep(fields).eval().edges().size();
					
					for(GraphElement field : fields.eval().nodes()){
						Q fieldQ = toQ(toGraph(field));
						fFlows += ipDF.reverseStep(fieldQ).eval().edges().size() * ipDF.forwardStep(fieldQ).eval().edges().size();
					}
				}

				writer.writeNext(new String[]{entry[0], entry[1], entry[2], Integer.toString(nodes), Integer.toString(edges), Integer.toString(sNodes), Integer.toString(sEdges), Integer.toString(fFlows), Integer.toString(oFlows)});
			}catch(Throwable t){
				Log.error("Summary experiment error", t);
			}
		}
		Log.info("Experiment done!");
		writer.flush();
		writer.close();
	}
}