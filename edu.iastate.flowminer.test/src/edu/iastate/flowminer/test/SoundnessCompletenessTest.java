package edu.iastate.flowminer.test;

import static com.ensoftcorp.atlas.core.script.Common.resolve;
import static com.ensoftcorp.atlas.core.script.Common.toGraph;
import static com.ensoftcorp.atlas.core.script.Common.toQ;
import static com.ensoftcorp.atlas.core.script.Common.universe;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.markup.IMarkup;
import com.ensoftcorp.atlas.core.markup.MarkupFromH;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.viewer.graph.SaveUtil;
import com.opencsv.CSVReader;

import edu.iastate.flowminer.actions.MineAndExport;
import edu.iastate.flowminer.schema.ISUSchema;

/**
 * Tests soundness and completeness of FlowMiner on a given library.
 * 
 * @author tdeering
 *
 */
@RunWith(value = Parameterized.class)
public class SoundnessCompletenessTest {
	private String toSummarize;
	private String[] dependencies;
	private String toSummarizeFilename;
	private File summaryOutputFile;
	private static final String PARSE_CHAR = ";";

	public SoundnessCompletenessTest(String toSummarize, String[] dependencies){
		this.toSummarize = toSummarize;
		this.toSummarizeFilename = new File(toSummarize).getName();
		this.dependencies = dependencies;
	}

	@Before
	public void setUp() throws Exception {
		summaryOutputFile = File.createTempFile("FlowMiner_" + System.currentTimeMillis(), ".xml.gz");
		new MineAndExport().doMineAndExport(new String[]{toSummarize}, dependencies, summaryOutputFile.getAbsolutePath(), JavaRuntime.getDefaultVMInstall(), true);
	}

	@After
	public void tearDown() throws Exception {
		summaryOutputFile.delete();
	}
	
	@Parameters(name = "{index}: {0}")
	public static Iterable<Object[]> data() throws IOException, URISyntaxException{
		URL url = new URL("platform:/plugin/edu.iastate.flowminer.test/test_resources/config.csv");
		BufferedReader in = new BufferedReader(new InputStreamReader(url.openConnection().getInputStream()));
		CSVReader reader = new CSVReader(in);
	    List<String[]> myEntries = reader.readAll();
	    reader.close();
	    
	    Object[][] data = new Object[myEntries.size()][];
	    int idx = 0;
	    for(String[] entry : myEntries){
	    	data[idx] = new Object[2];
	    	data[idx][0] = toSystemPaths(entry[0].split(PARSE_CHAR))[0];
	    	data[idx][1] = toSystemPaths(entry[1].split(PARSE_CHAR));
			++idx;
	    }

		return Arrays.asList(data);
	}
	
	@Test
	public void testEquivalentFlows() {
		Q u = universe();	
		Q ipdf = u.edgesTaggedWithAny(XCSG.InterproceduralDataFlow);
		Q atlasFlowContext = resolve(null, u.edgesTaggedWithAny(XCSG.DataFlow_Edge).differenceEdges(
				ipdf.forwardStep(u.nodesTaggedWithAny(XCSG.ReturnValue)),
				ipdf.reverseStep(u.nodesTaggedWithAny(XCSG.CallInput))).union(
				u.edgesTaggedWithAny(ISUSchema.Edge.FLOW_METHOD_RESOLVED)));
		Q summaryFlowContext = resolve(null, u.edgesTaggedWithAny(ISUSchema.Edge.FLOW));

		Q keyNodes = resolve(null, 
				u.edgesTaggedWithAny(XCSG.InstanceVariableAccess, XCSG.ArrayAccess, XCSG.ArrayIndexFor, XCSG.ArrayIdentityFor).retainEdges().union(
						ipdf.forwardStep(u.nodesTaggedWithAny(XCSG.Field)),
						ipdf.reverseStep(u.nodesTaggedWithAny(XCSG.Field)),
						u.nodesTaggedWithAny(XCSG.Variable, XCSG.CallInput, XCSG.Instantiation, XCSG.ArrayInstantiation, 
						XCSG.Literal, XCSG.ReturnValue, XCSG.ArrayComponents, ISUSchema.Node.LOCAL_CALL_SITE_RESOLVED)));
		
		for(GraphElement keyNode : keyNodes.eval().nodes()){
			Q keyNodeQ = toQ(toGraph(keyNode));
			Q atlasForward = atlasFlowContext.forward(keyNodeQ);
			Q summaryForward = summaryFlowContext.forward(keyNodeQ);
			AtlasSet<Node> atlasReached = atlasForward.intersection(keyNodes).eval().nodes();
			AtlasSet<Node> summaryReached = summaryForward.intersection(keyNodes).eval().nodes();
			if(!atlasReached.deepEquals(summaryReached)){
				Highlighter h = new Highlighter();
				h.highlight(atlasForward.difference(summaryForward).intersection(keyNodes), java.awt.Color.CYAN);
				h.highlight(summaryForward.difference(atlasForward).intersection(keyNodes), java.awt.Color.RED);
				
				Q problem = atlasForward.between(keyNodeQ, keyNodes).union(summaryForward.between(keyNodeQ, keyNodes));
				problem = problem.union(u.edgesTaggedWithAny(XCSG.Contains).reverse(problem));
				
				
				IMarkup markup = new MarkupFromH(h);
				SaveUtil.saveGraph(new File("/home/tdeering/" + toSummarizeFilename + "_" + System.currentTimeMillis() + ".png"), problem.eval(), markup);
				
				fail("Different sets of key nodes are forward reachable from:\n" + keyNode);
			}
		}
	}
	
	private static String[] toSystemPaths(String[] platformURLs) throws URISyntaxException, IOException{
		String[] res = new String[platformURLs.length];
		for(int i = 0; i < platformURLs.length; ++i){
			if(platformURLs[i].length() == 0){
				res[i] = platformURLs[i];
			}else{
				URL url  = new URL(platformURLs[i]);
				File f = new File(FileLocator.resolve(url).toURI());
				res[i] = f.getAbsolutePath();
			}
		}
		return res;
	}
}