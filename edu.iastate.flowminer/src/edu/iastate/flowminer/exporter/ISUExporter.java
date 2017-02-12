package edu.iastate.flowminer.exporter;

import static com.ensoftcorp.atlas.core.script.Common.resolve;
import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.notification.NotificationMap;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.java.core.script.Common;

import edu.iastate.flowminer.exception.FlowMinerException;
import edu.iastate.flowminer.io.model.Element;
import edu.iastate.flowminer.io.model.IOModel;
import edu.iastate.flowminer.io.model.LocalVarElement;
import edu.iastate.flowminer.io.model.MethodElement;
import edu.iastate.flowminer.io.model.Relationship;
import edu.iastate.flowminer.log.Log;
import edu.iastate.flowminer.schema.ISUSchema;

public class ISUExporter extends Exporter{	
	private Graph decContext = resolve(null, universe().edgesTaggedWithAny(XCSG.Contains).eval());
	private Graph typeofContext = resolve(null, universe().edgesTaggedWithAny(XCSG.TypeOf).eval());
	private Graph elementTypeContext = resolve(null, universe().edgesTaggedWithAny(XCSG.ArrayElementType).eval());
	private GraphElement voidType = universe().nodesTaggedWithAny(XCSG.Void).eval().nodes().getFirst();
	private Map<Node, Element> exported;
	
	@Override
	protected void exportSummary(IProgressMonitor mon,
			Map<Node, Element> exported, IOModel model, Q toExport) {
		// ASSERT: AtlasExporter has already exported the things it supports from toExport
		if(mon.isCanceled()) return;
		SubMonitor sm = SubMonitor.convert(mon, 211392);
		try{
			Log.info("ISUExporter exporting");
			this.exported = exported;
			
			if(mon.isCanceled()) return;
			Log.info("ISUExporter selecting relevant portion to export");
			Q actualExport = selectRelevant(sm.newChild(1), toExport);
			
			if(mon.isCanceled()) return;
			Log.info("ISUExporter enumerating graph to export");
			Graph actualExportG = resolve(sm.newChild(167937), actualExport.eval());
			AtlasSet<Node> nodesToExport = actualExportG.nodes().taggedWithAny(ISUSchema.ISU);
			AtlasSet<Edge> edgesToExport = actualExportG.edges().taggedWithAny(ISUSchema.ISU);

			// Create DOMConvertables for nodes
			if(mon.isCanceled()) return;
			Log.info("ISUExporter converting nodes to model elements");
			convertNodesToElements(sm.newChild(30433), nodesToExport);

			// Create DOMConvertables for edges
			if(mon.isCanceled()) return;
			Log.info("ISUExporter converting edges to model relationships");
			convertEdgesToRelationships(sm.newChild(13020), model, edgesToExport);
		}finally{
			mon.done();
		}
	}
	
	@Override
	protected Q contributeExports(IProgressMonitor mon, Q contributeFor) {
		if(mon.isCanceled()) return null;
		try{
			Log.info("ISUExporter contributing exports");
			return selectRelevant(mon, Common.universe());
		}finally{
			mon.done();
		}
	}
	
	private Q selectRelevant(IProgressMonitor mon, Q choose){
		if(mon.isCanceled()) return null;
				
		return choose.nodesTaggedWithAny(ISUSchema.ISU).union(
			   choose.edgesTaggedWithAny(ISUSchema.ISU).retainEdges());
	}
	
	private void convertNodesToElements(IProgressMonitor mon, AtlasSet<Node> nodesToExport){
		if(mon.isCanceled()) return;
		AtlasSet<Node> exportSet = nodesToExport.taggedWithAny(ISUSchema.Node.LOCAL);
		SubMonitor sm = SubMonitor.convert(mon, (int) exportSet.size());
		
		HashSet<GraphElement> missingTypes = new HashSet<GraphElement>();
		
		try{
			for(GraphElement ge : exportSet){
				if(mon.isCanceled()) return;
				LocalVarElement e = new LocalVarElement(null, (String) ge.attr().get(ISUSchema.SCHEMA_TYPE));
				
				NotificationMap<String, Object> geAttr = ge.attr();
				e.setSchemaType((String) geAttr.get(ISUSchema.SCHEMA_TYPE)); 
				
				Integer paramIdx = (Integer) ge.getAttr(XCSG.parameterIndex);
				if(paramIdx != null){
					e.setParamIdx(paramIdx);
				}
				
				if(ge.tags().contains(XCSG.Literal)){
					e.setName((String) ge.attr().get(XCSG.name));
				}

				exported.put((Node) ge, e);
				GraphElement declaringMethod = firstDeclarator(ge, XCSG.Method);
				MethodElement me = (MethodElement) exported.get(declaringMethod);
				me.getLocalVar().add(e);
				
				GraphElement type = typeofContext.edges(ge, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO);
				if(type.tags().contains(XCSG.ArrayType)){
					e.setArray_dim((int) type.attr().get(Attr.Node.DIMENSION));
					type = elementTypeContext.edges(type, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO);
				}
				
				if(type != voidType) {
					Element element = exported.get(type);
					if (element == null) {
						missingTypes.add(type);
					} else {
						e.setType(element.getId());
					}
				}
				sm.worked(1);
			}
			
			if (!missingTypes.isEmpty()) {
				ArrayList<GraphElement> missingTypesList = new ArrayList<GraphElement>(); 
				missingTypesList.addAll(missingTypes);
				Collections.sort(missingTypesList, new Comparator<GraphElement>() {
					@Override
					public int compare(GraphElement o1, GraphElement o2) {
						String name1 = (String) o1.getAttr(XCSG.name);
						String name2 = (String) o2.getAttr(XCSG.name);
						int c = String.CASE_INSENSITIVE_ORDER.compare(name1, name2);
						if (c!=0)
							return c;
						return o1.address().compareTo(o2.address());
					}
				});
				Log.warning("Missing dependencies, reference to types which were not exported:\n" + missingTypesList.toString());
			}
		}finally{
			sm.done();
		}
	}
	
	private void convertEdgesToRelationships(IProgressMonitor mon, IOModel model, AtlasSet<Edge> edgesToExport){
		if(mon.isCanceled()) return;
		AtlasSet<Edge> exportSet = edgesToExport.taggedWithAny(ISUSchema.ISU);
		SubMonitor sm = SubMonitor.convert(mon, (int) exportSet.size());
		try{			
			for(GraphElement ge : exportSet){
				if(mon.isCanceled()) return;
				GraphElement from = ge.getNode(EdgeDirection.FROM);
				GraphElement to = ge.getNode(EdgeDirection.TO);
				
				if(exported.get(from) == null){
					throw new FlowMinerException("Cannot find edge endpoint:\n" + ge + "\n" + from, new RuntimeException());
				} else if(exported.get(to) == null){
					throw new FlowMinerException("Cannot find edge endpoint:\n" + ge + "\n" + to, new RuntimeException());
				}
				
				Relationship r = new Relationship(null, exported.get(from).getId(), exported.get(to).getId(), (String) ge.attr().get(ISUSchema.SCHEMA_TYPE));
				model.getRelationship().add(r);

				sm.worked(1);
			}
		}finally{
			sm.done();
		}
	}
	
	private GraphElement firstDeclarator(GraphElement ge, String tag){
		while(true){
			GraphElement decEdge = decContext.edges(ge, NodeDirection.IN).getFirst();
			if(decEdge == null) return null;
			ge = decEdge.getNode(EdgeDirection.FROM);
			if(ge.tags().contains(tag)) return ge;
		}
	}
}