package edu.iastate.flowminer.miner;

import static com.ensoftcorp.atlas.core.script.Common.toGraph;
import static com.ensoftcorp.atlas.core.script.Common.toQ;
import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.NodeGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.db.set.IntersectionSet;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.UniverseManipulator;
import com.ensoftcorp.atlas.core.script.UniverseManipulator.Manipulation;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.java.core.script.Common;

import edu.iastate.flowminer.exception.FlowMinerException;
import edu.iastate.flowminer.log.Log;
import edu.iastate.flowminer.schema.ISUSchema;
import edu.iastate.flowminer.utility.CRunnable;
import edu.iastate.flowminer.utility.PrecedenceGraph;
import edu.iastate.flowminer.utility.ThreadPool;
import net.ontopia.utils.CompactHashSet;

public class ISUMiner extends Miner{
	private Set<SummaryEdge> summaryEdges = Collections.synchronizedSet(new CompactHashSet<SummaryEdge>());
	
	/**
	 * Edge contexts
	 */
	private Q containsContext, arrayIdentityContext, arrayIndexContext, localDFContext, interDFContext, passedToContext, elementsIteratedContext, fieldAccessContext,
		instanceVariableAccessContext, arrayFlowContext, callsites, arrayComponents,
		resolvableCallsites, parameterPassedToContext, identityPassedToContext, invokedSignatureContext, invokedTypeContext, typeOfContext, concreteMethods, concreteMethodDecs;
	
	/**
	 * Node contexts
	 */
	private Q fields;
	
	private AtlasSet<Node> keyNodes;
	private GraphElement voidType; 
	
	@Override
	public String toString() {
		return "ISUMiner";
	}
	
	@Override
	public void mineSummary(IProgressMonitor mon, UniverseManipulator um) throws Throwable {
		try{
			SubMonitor sm = SubMonitor.convert(mon, 202261);
			
			/*
			 * ********** STAGE 1: Preparation of contexts **********
			 */
			prepareContextsForMining(sm.newChild(75640));
			
			/*
			 * ********** STAGE 2: Execution of summary mining **********
			 */
			executeMining(sm.newChild(115399), um);
			
			/*
			 * ********** STAGE 3: Write the completed summaries to the index **********
			 */
			modifyIndex(sm.newChild(11222), um);
		}finally{
			mon.done();
		}
	}
	
	private void prepareContextsForMining(IProgressMonitor mon) throws Throwable{
		if(mon.isCanceled()) return;
		final SubMonitor sm = SubMonitor.convert(mon, 20);
		try{
			if(sm.isCanceled()) return;
			Log.info("ISUMiner preparing contexts");
			
			PrecedenceGraph pg = new PrecedenceGraph();
			final Q u = universe();
			
			CRunnable r0 = new CRunnable(sm, new Runnable(){public void run() {
				containsContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.Contains).differenceEdges(u.reverseStep(u.nodesTaggedWithAny(XCSG.Type))));		
			}});
			pg.add(r0);
			
			CRunnable r15 = new CRunnable(sm, new Runnable(){public void run() {
				localDFContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.LocalDataFlow));
			}});
			pg.add(r15);
			
			CRunnable r1 = new CRunnable(sm, new Runnable(){public void run() {
				passedToContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.PassedTo));
			}});
			pg.add(r1);
			
			CRunnable r20 = new CRunnable(sm, new Runnable(){public void run() {
				elementsIteratedContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.ElementsIterated));
			}});
			pg.add(r20);
			
			CRunnable r2 = new CRunnable(sm, new Runnable(){public void run() {
				parameterPassedToContext = Common.resolve(null, passedToContext.edgesTaggedWithAny(XCSG.ParameterPassedTo));
			}});
			pg.add(r2);
			pg.addPrecedence(r1, r2);
			
			CRunnable r3 = new CRunnable(sm, new Runnable(){public void run() {
				identityPassedToContext = Common.resolve(null, passedToContext.edgesTaggedWithAny(XCSG.IdentityPassedTo));
			}});
			pg.add(r3);
			pg.addPrecedence(r1, r3);
			
			pg.add(new CRunnable(sm, new Runnable(){public void run() {
				typeOfContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.TypeOf));
			}}));

			CRunnable r17 = new CRunnable(sm, new Runnable(){public void run() {
				invokedSignatureContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.InvokedSignature, XCSG.InvokedFunction));
			}});
			pg.add(r17);
			
			CRunnable r18 = new CRunnable(sm, new Runnable(){public void run() {
				invokedTypeContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.InvokedType));
			}});
			pg.add(r18);
			
			CRunnable r4 = new CRunnable(sm, new Runnable(){public void run() {
				interDFContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.InterproceduralDataFlow));
			}});
			pg.add(r4);
			
			CRunnable r5 = new CRunnable(sm, new Runnable(){public void run() {
				fields = Common.resolve(null, u.nodesTaggedWithAny(XCSG.Field));
			}});
			pg.add(r5);
			
			CRunnable r6 = new CRunnable(sm, new Runnable(){public void run() {
				fieldAccessContext = Common.resolve(null, interDFContext.forwardStep(fields).union(interDFContext.reverseStep(fields)));
			}});
			pg.add(r6);
			pg.addPrecedence(r4,r6);
			pg.addPrecedence(r5,r6);
			
			CRunnable r23 = new CRunnable(sm, new Runnable(){public void run() {
				instanceVariableAccessContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.InstanceVariableAccess));
			}});
			pg.add(r23);
			
			CRunnable r7 = new CRunnable(sm, new Runnable(){public void run() {
				callsites = Common.resolve(null, u.nodesTaggedWithAny(XCSG.CallSite));
			}});
			pg.add(r7);
			
			CRunnable r16 = new CRunnable(sm, new Runnable(){public void run() {				
				Q types = u.nodesTaggedWithAny(XCSG.Type);
				Q properTypes = types.difference(
						u.nodesTaggedWithAny(XCSG.Primitive, Attr.Node.NULL_TYPE, XCSG.ArrayType, XCSG.Void));

				Q nonLibNoSubtype = u.edgesTaggedWithAny(XCSG.Supertype).roots().intersection(properTypes);
				Q staticDispatches = u.nodesTaggedWithAny(XCSG.StaticDispatchCallSite);
				Q methodsMarkedInextensible = u.nodesTaggedWithAny(Attr.Node.IS_FINAL, XCSG.privateVisibility);
				Q typesMarkedInextensible = u.nodesTaggedWithAny(XCSG.Java.finalClass, XCSG.ArrayType, XCSG.Java.AnonymousClass, XCSG.Java.LocalClass);
				
				AtlasSet<Node> resolvableCallsiteSet = new AtlasHashSet<Node>(staticDispatches.union(
					invokedSignatureContext.predecessors(methodsMarkedInextensible),
					invokedTypeContext.predecessors(typesMarkedInextensible.union(nonLibNoSubtype))).
					nodesTaggedWithAny(XCSG.CallSite).eval().nodes());

				resolvableCallsites = toQ(new NodeGraph(resolvableCallsiteSet));
			}});
			pg.add(r16);
			pg.addPrecedence(r17,r16);
			pg.addPrecedence(r18,r16);
			
			CRunnable r14 = new CRunnable(sm, new Runnable(){public void run() {
				arrayComponents = Common.resolve(null, u.nodesTaggedWithAny(XCSG.ArrayComponents));	
			}});
			pg.add(r14);
			
			pg.add(new CRunnable(sm, new Runnable(){public void run() {
				voidType = u.nodesTaggedWithAny(XCSG.Void).eval().nodes().getFirst();
			}}));
			
			CRunnable r11 = new CRunnable(sm, new Runnable(){public void run() {
				concreteMethods = Common.resolve(null, u.nodesTaggedWithAny(XCSG.Method).difference(u.nodesTaggedWithAny(XCSG.abstractMethod)));		
			}});
			pg.add(r11);
			
			CRunnable r12 = new CRunnable(sm, new Runnable(){public void run() {
				concreteMethodDecs = Common.resolve(null, containsContext.forward(concreteMethods));		
			}});
			pg.add(r12);
			pg.addPrecedence(r0, r12);
			pg.addPrecedence(r11, r12);
			
			CRunnable r21 =new CRunnable(sm, new Runnable(){public void run() {
				arrayIndexContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.ArrayIndexFor));
			}});
			pg.add(r21);
			
			CRunnable r22 =new CRunnable(sm, new Runnable(){public void run() {
				arrayIdentityContext = Common.resolve(null, u.edgesTaggedWithAny(XCSG.ArrayIdentityFor));
			}});
			pg.add(r22);
			
			CRunnable r19 = new CRunnable(sm, new Runnable(){public void run() {
				arrayFlowContext = Common.resolve(null, interDFContext.forwardStep(arrayComponents).union(interDFContext.reverseStep(arrayComponents)));
			}});
			pg.add(r19);
			pg.addPrecedence(r4, r19);
			pg.addPrecedence(r14, r19);
			
			CRunnable r10 = new CRunnable(sm, new Runnable(){public void run() {
				Q keyCallsites = callsites.difference(resolvableCallsites).union(
						interDFContext.successors(u.nodesTaggedWithAny(XCSG.ReturnValue)).intersection(resolvableCallsites));
				
				keyNodes = new AtlasHashSet<Node>(keyCallsites.union(
						// Stack Items
						passedToContext.retainEdges(),
						// Field instance variables
						instanceVariableAccessContext.retainEdges(),
						// Values read and written to/from array components
						arrayFlowContext.retainEdges(),
						// Array indices used by access operators
						arrayIndexContext.retainEdges(),
						// Array references used by access operators
						arrayIdentityContext.retainEdges(),
						// Signature elements + literals
						u.nodesTaggedWithAny(XCSG.Instantiation, XCSG.ArrayInstantiation, XCSG.Literal, XCSG.CallInput, XCSG.ReturnValue),
						// Values read from and written to fields
						fieldAccessContext.retainEdges(),
						// For each array/iterable reference and receiver variable
						elementsIteratedContext.retainEdges()).eval().nodes());
			}});
			pg.add(r10);
			pg.addPrecedence(r7, r10);
			pg.addPrecedence(r1, r10);
			pg.addPrecedence(r5, r10);
			pg.addPrecedence(r6, r10);
			pg.addPrecedence(r4, r10);
			pg.addPrecedence(r14, r10);
			pg.addPrecedence(r15, r10);
			pg.addPrecedence(r16, r10);
			pg.addPrecedence(r19, r10);
			pg.addPrecedence(r20, r10);
			pg.addPrecedence(r21, r10);
			pg.addPrecedence(r22, r10);
			pg.addPrecedence(r23, r10);
			
			if(sm.isCanceled()) return;
			pg.execute(true);	
		}finally{
			sm.done();
		}
	}
	
	private void executeMining(IProgressMonitor mon, UniverseManipulator um) throws Throwable{
		try{
			if(mon.isCanceled()) return;
			Log.info("ISUMiner commencing mining operations");
			
			SubMonitor sm = SubMonitor.convert(mon, 10);
			LinkedList<Future<?>> futures = new LinkedList<Future<?>>();
			
			if(sm.isCanceled()) return;
			mineLocalFlows(sm.newChild(1), futures, um);
			if(sm.isCanceled()) return;
			mineFieldFlows(sm.newChild(1), futures);
			if(sm.isCanceled()) return;
			mineInstanceVariableAccess(sm.newChild(1), futures);
			if(sm.isCanceled()) return;
			mineArrayFlows(sm.newChild(1), futures, um);
			if(sm.isCanceled()) return;
			mineArrayIndices(sm.newChild(1), futures, um);
			if(sm.isCanceled()) return;
			mineArrayIdentities(sm.newChild(1), futures, um);
			if(sm.isCanceled()) return;
			mineMethodFlows(sm.newChild(2), futures, callsites);
			if(sm.isCanceled()) return;
			schemifyExistingNodes(sm.newChild(1), futures, um);
			if(sm.isCanceled()) return;
			mineArrayAccess(sm.newChild(1), futures, um);
			
			if(sm.isCanceled()) return;
			ThreadPool.blockUntilAllComplete(true, futures);
		} finally{
			mon.done();
		}
	}
	
	private void modifyIndex(IProgressMonitor mon, UniverseManipulator um){
		try{
			SubMonitor sm = SubMonitor.convert(mon, summaryEdges.size());
			if(sm.isCanceled()) return;
			Log.info("ISUMiner describing index modifications");
			
			Map<String, Object> attr = new HashMap<String, Object>();
			
			for(SummaryEdge se : summaryEdges){
				if(sm.isCanceled()) return;
				attr.put(XCSG.name, ISUSchema.getDisplayName(se.type));
				attr.put(ISUSchema.SCHEMA_TYPE, se.type);
				Manipulation m = um.createEdge(se.tags, attr, se.origin, se.dest);
				if(se.extraTag != null) um.addTag(m, se.extraTag);
				sm.worked(1);
			}
		} finally{
			mon.done();
		}
	}
	
	private void schemifyExistingNodes(IProgressMonitor mon, List<Future<?>> futures, final UniverseManipulator um){
		SubMonitor sm = SubMonitor.convert(mon, 1);
		futures.add(ThreadPool.submitRunnables(new CRunnable(sm, new Runnable(){public void run() {
			AtlasSet<Node> mKeyDF = keyNodes;
			AtlasSet<Node> resolvableCallsiteSet = resolvableCallsites.eval().nodes();
			for(GraphElement ge : mKeyDF){
				if(!ge.tags().containsAny(XCSG.Parameter, XCSG.ReturnValue, XCSG.Identity)){
					String sType = null;
					if(ge.taggedWith(XCSG.ArrayComponents)){	
						sType = ISUSchema.Node.LOCAL_ARRAY_COMPONENT;
					}else if(ge.taggedWith(XCSG.CallSite)){
						if(resolvableCallsiteSet.contains(ge)){
							sType = ISUSchema.Node.LOCAL_CALL_SITE_RESOLVED;
						}else{
							sType = ISUSchema.Node.LOCAL_CALL_SITE_UNRESOLVED;
						}
					}else if(ge.taggedWith(XCSG.Literal)){
						sType = ISUSchema.Node.LOCAL_LITERAL;
					}else if(ge.taggedWith(XCSG.ArrayAccess)){
						sType = ISUSchema.Node.LOCAL_ARRAY_INDEX_OP;
					}else if(ge.taggedWith(XCSG.DataFlow_Node)){
						sType = ISUSchema.Node.LOCAL;
					}
					
					if(sType != null){
						ge.putAttr(ISUSchema.SCHEMA_TYPE, sType);
						for(String s : ISUSchema.getTags(sType)) ge.tag(s);
					}
				}
			}	
		}}))[0]);
	}
	
	private void mineArrayAccess(IProgressMonitor mon, List<Future<?>> futures, final UniverseManipulator um){
		SubMonitor sm = SubMonitor.convert(mon, 1);
		futures.add(ThreadPool.submitRunnables(new CRunnable(sm, new Runnable(){public void run() {
			LinkedList<SummaryEdge> toAdd = new LinkedList<SummaryEdge>();
			
			for(GraphElement ge : arrayIdentityContext.eval().edges()){
				GraphElement from = ge.getNode(EdgeDirection.FROM);
				GraphElement to = ge.getNode(EdgeDirection.TO);
				toAdd.add(new SummaryEdge(from, to, ISUSchema.Edge.ARRAY_IDENTITY, ISUSchema.EDGE_ARRAY_IDENTITY_TAGS));
			}	
			
			for(GraphElement ge : arrayIndexContext.eval().edges()){
				GraphElement from = ge.getNode(EdgeDirection.FROM);
				GraphElement to = ge.getNode(EdgeDirection.TO);
				toAdd.add(new SummaryEdge(from, to, ISUSchema.Edge.ARRAY_INDEX, ISUSchema.EDGE_ARRAY_INDEX_TAGS));
			}
			
			if(!toAdd.isEmpty()) summaryEdges.addAll(toAdd);
		}}))[0]);
	}
	
	private void mineLocalFlows(IProgressMonitor mon, List<Future<?>> futures, final UniverseManipulator um){		
		final SubMonitor sm = SubMonitor.convert(mon, (int) concreteMethods.eval().nodes().size());
		final Graph dfLocalContextG = localDFContext.eval();
		
		for(final GraphElement concreteMethod : concreteMethods.eval().nodes()){
			futures.add(ThreadPool.submitRunnables(new CRunnable(sm, new Runnable(){public void run() {
				AtlasSet<Node> cmDecs = 
						concreteMethodDecs.intersection(containsContext.forward(toQ(toGraph(concreteMethod)))).eval().nodes();
				
				AtlasSet<GraphElement> mKeyNodes = new AtlasHashSet<GraphElement>(new IntersectionSet<GraphElement>(cmDecs, keyNodes));
				LinkedList<SummaryEdge> toAdd = new LinkedList<SummaryEdge>();

				AtlasSet<GraphElement> arrayRef = new AtlasHashSet<GraphElement>();
				AtlasSet<GraphElement> arrayIdx = new AtlasHashSet<GraphElement>();
				for(GraphElement ge : mKeyNodes){
					arrayRef.clear();
					arrayIdx.clear();
					
					// Find other key nodes that this node reaches
					AtlasSet<Node> reached = localFlow(dfLocalContextG, ge, mKeyNodes, arrayRef, arrayIdx);
					
					for(GraphElement ge2 : reached){
						toAdd.add(new SummaryEdge(ge, ge2, ISUSchema.Edge.FLOW_LOCAL, ISUSchema.EDGE_FLOW_LOCAL_TAGS, null));
					}
				}
				
				if(!toAdd.isEmpty()) summaryEdges.addAll(toAdd);
			}}))[0]);
		}
	}
	
	private void mineArrayFlows(IProgressMonitor mon, List<Future<?>> futures, final UniverseManipulator um){		
		final SubMonitor sm = SubMonitor.convert(mon, (int) arrayFlowContext.eval().edges().size());
		
		futures.add(ThreadPool.submitRunnables(new CRunnable(null, new Runnable(){public void run() {
			LinkedList<SummaryEdge> toAdd = new LinkedList<SummaryEdge>();
			
			for(GraphElement access : arrayFlowContext.eval().edges()){
				if(sm.isCanceled()) return;
				toAdd.add(new SummaryEdge(access.getNode(EdgeDirection.FROM), access.getNode(EdgeDirection.TO), ISUSchema.Edge.FLOW_ARRAY, ISUSchema.EDGE_FLOW_ARRAY_TAGS));
				sm.worked(1);
			}
			
			if(!toAdd.isEmpty()) summaryEdges.addAll(toAdd);
		}}))[0]);
	}
	
	private void mineArrayIndices(IProgressMonitor mon, List<Future<?>> futures, final UniverseManipulator um){
		final AtlasSet<Edge> edges = arrayIndexContext.eval().edges();
		final SubMonitor sm = SubMonitor.convert(mon, (int) edges.size());
		
		futures.add(ThreadPool.submitRunnables(new CRunnable(null, new Runnable(){public void run() {
			LinkedList<SummaryEdge> toAdd = new LinkedList<SummaryEdge>();
			
			for(GraphElement access : edges){
				if(sm.isCanceled()) return;
				toAdd.add(new SummaryEdge(access.getNode(EdgeDirection.FROM), access.getNode(EdgeDirection.TO), ISUSchema.Edge.ARRAY_INDEX, ISUSchema.EDGE_ARRAY_INDEX_TAGS));
				sm.worked(1);
			}
			
			if(!toAdd.isEmpty()) summaryEdges.addAll(toAdd);
		}}))[0]);
	}
	
	private void mineArrayIdentities(IProgressMonitor mon, List<Future<?>> futures, final UniverseManipulator um){
		final AtlasSet<Edge> edges = arrayIdentityContext.eval().edges();
		final SubMonitor sm = SubMonitor.convert(mon, (int) edges.size());
		
		futures.add(ThreadPool.submitRunnables(new CRunnable(null, new Runnable(){public void run() {
			LinkedList<SummaryEdge> toAdd = new LinkedList<SummaryEdge>();
			
			for(GraphElement access : edges){
				if(sm.isCanceled()) return;
				toAdd.add(new SummaryEdge(access.getNode(EdgeDirection.FROM), access.getNode(EdgeDirection.TO), ISUSchema.Edge.ARRAY_IDENTITY, ISUSchema.EDGE_ARRAY_IDENTITY_TAGS));
				sm.worked(1);
			}
			
			if(!toAdd.isEmpty()) summaryEdges.addAll(toAdd);
		}}))[0]);
	}
	
	private AtlasSet<Node> localFlow(Graph context, GraphElement origin, AtlasSet<GraphElement> keyNodes, 
			AtlasSet<GraphElement> arrayRef, AtlasSet<GraphElement> arrayIdx){
		int setSize = (int) (keyNodes.size() * 2);
		AtlasSet<Node> result = new AtlasHashSet<Node>(Graph.U, setSize);
		AtlasSet<Node> reached = new AtlasHashSet<Node>(Graph.U, setSize);
		AtlasSet<Node> newFrontier = new AtlasHashSet<Node>(Graph.U, setSize);
		AtlasSet<Node> frontier = new AtlasHashSet<Node>(Graph.U, setSize);
		frontier.add(origin);
		
		while(!frontier.isEmpty()){				
			for(GraphElement frontierNode : frontier){
				for(GraphElement flowEdge : context.edges(frontierNode, NodeDirection.OUT)){
					GraphElement reachedNode = flowEdge.getNode(EdgeDirection.TO);
					if(keyNodes.contains(reachedNode)){
						result.add(reachedNode);
						if(reachedNode.taggedWith(XCSG.ArrayAccess)){
							(flowEdge.taggedWith(XCSG.leftOperand) ? arrayRef:arrayIdx).add(reachedNode);
						}
					}else if(!reached.contains(reachedNode)){
						newFrontier.add(reachedNode);
						reached.add(reachedNode);
					}
				}
			}
			
			AtlasSet<Node> tmp = frontier;
			frontier = newFrontier;
			newFrontier = tmp;
			newFrontier.clear();
		}
	
		return result;
	}

	private void mineFieldFlows(final IProgressMonitor mon, List<Future<?>> futures){
		final AtlasSet<Edge> fieldAccessEdges = fieldAccessContext.eval().edges();
		final SubMonitor sm = SubMonitor.convert(mon, (int) fieldAccessEdges.size());
		
		futures.add(ThreadPool.submitRunnables(new CRunnable(null, new Runnable(){public void run() {
			LinkedList<SummaryEdge> toAdd = new LinkedList<SummaryEdge>();
			
			for(GraphElement access : fieldAccessEdges){
				if(sm.isCanceled()) return;
				toAdd.add(new SummaryEdge(access.getNode(EdgeDirection.FROM), access.getNode(EdgeDirection.TO), ISUSchema.Edge.FLOW_FIELD, ISUSchema.EDGE_FLOW_FIELD_TAGS));
			}
			
			if(!toAdd.isEmpty()) summaryEdges.addAll(toAdd);
		}}))[0]);
	}
	
	private void mineInstanceVariableAccess(final IProgressMonitor mon, List<Future<?>> futures){
		final AtlasSet<Edge> instanceVariableAccessEdges = instanceVariableAccessContext.eval().edges();
		final SubMonitor sm = SubMonitor.convert(mon, (int) instanceVariableAccessEdges.size());
		
		futures.add(ThreadPool.submitRunnables(new CRunnable(null, new Runnable(){public void run() {
			LinkedList<SummaryEdge> toAdd = new LinkedList<SummaryEdge>();
			
			for(GraphElement access : instanceVariableAccessEdges){
				if(sm.isCanceled()) return;
				toAdd.add(new SummaryEdge(access.getNode(EdgeDirection.FROM), access.getNode(EdgeDirection.TO), ISUSchema.Edge.INSTANCE_VARIABLE_ACCESS, ISUSchema.INSTANCE_VARIABLE_ACCESS_TAGS));
			}
			
			if(!toAdd.isEmpty()) summaryEdges.addAll(toAdd);
		}}))[0]);
	}
	
	private void mineMethodFlows(IProgressMonitor mon, List<Future<?>> futures, Q callsites){
		AtlasSet<Node> callsiteSet = callsites.eval().nodes();
		final SubMonitor sm = SubMonitor.convert(mon, (int) callsiteSet.size());
		
		final AtlasSet<Node> singletonCallsiteSet = resolvableCallsites.eval().nodes();
		
		// Find the features of this method's signature
		for(final GraphElement dfi : callsiteSet){
			futures.add(ThreadPool.submitRunnables(new CRunnable(sm, new Runnable(){public void run() {
				boolean isSingleton = singletonCallsiteSet.contains(dfi);
				
				// Find the local stack items for this invocation
				GraphElement dfiThis = null;
				GraphElement dfiThisEdge = identityPassedToContext.eval().edges(dfi, NodeDirection.IN).getFirst();
				if(dfiThisEdge != null) dfiThis = dfiThisEdge.getNode(EdgeDirection.FROM);
				
				AtlasSet<GraphElement> dfiParamEdges = parameterPassedToContext.eval().edges(dfi, NodeDirection.IN);
				AtlasSet<Node> dfiParams = new AtlasHashSet<Node>(Graph.U, (int) dfiParamEdges.size());
				for(GraphElement dfiParamEdge : dfiParamEdges) dfiParams.add(dfiParamEdge.getNode(EdgeDirection.FROM));

				// Find the features of the destination method's signature
				GraphElement dMethod = invokedSignatureContext.eval().edges(dfi, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO);
				AtlasSet<Node> dDeclared = new AtlasHashSet<Node>(containsContext.forwardStep(toQ(toGraph(dMethod))).eval().nodes());
				GraphElement dReturn = dDeclared.taggedWithAny(XCSG.ReturnValue).getFirst();
				GraphElement dThis = dDeclared.taggedWithAny(XCSG.Identity).getFirst();
				AtlasSet<Node> dParams = dDeclared.taggedWithAny(XCSG.Parameter);

				GraphElement ttsEdge = invokedTypeContext.eval().edges(dfi, NodeDirection.OUT).getFirst();
				GraphElement dTTS = ttsEdge == null ? null:ttsEdge.getNode(EdgeDirection.TO);
				
				LinkedList<SummaryEdge> toAdd = new LinkedList<SummaryEdge>();
				
				if(dfiThis != null){
					if(dThis == null) throw new FlowMinerException("Unable to find signature this for:\n" + dMethod);
					if(isSingleton){
						toAdd.add(new SummaryEdge(dfiThis, dThis, ISUSchema.Edge.FLOW_METHOD_RESOLVED, ISUSchema.EDGE_FLOW_METHOD_RESOLVED_TAGS));
					} else{
						toAdd.add(new SummaryEdge(dfiThis, dfi, ISUSchema.Edge.DYNAMIC_CALLSITE_THIS, ISUSchema.EDGE_DYNAMIC_CALLSITE_THIS_TAGS));
					}
				}
				
				if(isSingleton && voidType != typeOfContext.eval().edges(dfi, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO)){
					if(dReturn == null) throw new FlowMinerException("Unable to find signature return for:\n" + dMethod);
					toAdd.add(new SummaryEdge(dReturn, dfi, ISUSchema.Edge.FLOW_METHOD_RESOLVED, ISUSchema.EDGE_FLOW_METHOD_RESOLVED_TAGS));
				}
				
				for(GraphElement dfiParam : dfiParams){
					GraphElement dParam = dParams.filter(XCSG.parameterIndex, dfiParam.getAttr(XCSG.parameterIndex)).getFirst();
					if(dParam == null) throw new FlowMinerException("Unable to find signature param for:\n" + dfiParam + "\n" + dMethod);
					if(isSingleton){
						toAdd.add(new SummaryEdge(dfiParam, dParam, ISUSchema.Edge.FLOW_METHOD_RESOLVED, ISUSchema.EDGE_FLOW_METHOD_RESOLVED_TAGS));
					}else{
						toAdd.add(new SummaryEdge(dfiParam, dfi, ISUSchema.Edge.DYNAMIC_CALLSITE_PARAM, ISUSchema.EDGE_DYNAMIC_CALLSITE_PARAM_TAGS));
					}
				}
				
				if(!isSingleton){
					toAdd.add(new SummaryEdge(dfi, dTTS, ISUSchema.Edge.DYNAMIC_CALLSITE_TYPE, ISUSchema.EDGE_DYNAMIC_CALLSITE_TYPE_TAGS));
					toAdd.add(new SummaryEdge(dfi, dMethod, ISUSchema.Edge.DYNAMIC_CALLSITE_SIGNATURE, ISUSchema.EDGE_DYNAMIC_CALLSITE_SIGNATURE_TAGS));
				}
				
				if(!toAdd.isEmpty()) summaryEdges.addAll(toAdd);
			}}))[0]);
		}
	}
	
	private class SummaryEdge{
		String type;
		GraphElement origin;
		GraphElement dest;
		Set<String> tags;
		String extraTag;
		
		public SummaryEdge(GraphElement origin, GraphElement dest, String type, Set<String> tags){
			this.origin = origin;
			this.dest = dest;
			this.type = type;
			this.tags = tags;
		}
		
		public SummaryEdge(GraphElement origin, GraphElement dest, String type, Set<String> tags, String extraTag){
			this(origin, dest, type,  tags);
			this.extraTag = extraTag;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((dest == null) ? 0 : dest.hashCode());
			result = prime * result
					+ ((type == null) ? 0 : type.hashCode());

			result = prime * result
					+ ((origin == null) ? 0 : origin.hashCode());
			result = prime * result
					+ ((tags == null) ? 0 : tags.hashCode());
			result = prime * result
					+ ((extraTag == null) ? 0 : extraTag.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SummaryEdge other = (SummaryEdge) obj;
			if (dest == null) {
				if (other.dest != null)
					return false;
			} else if (!dest.equals(other.dest))
				return false;
			if (type == null) {
				if (other.type != null)
					return false;
			} else if (!type.equals(other.type))
				return false;
			if (origin == null) {
				if (other.origin != null)
					return false;
			} else if (!origin.equals(other.origin))
				return false;
			if (tags == null) {
				if (other.tags != null)
					return false;
			} else if (!tags.equals(other.tags))
				return false;
			if (extraTag == null) {
				if (other.extraTag != null)
					return false;
			} else if(!extraTag.equals(other.extraTag))
				return false;
			return true;
		}
	}
}
