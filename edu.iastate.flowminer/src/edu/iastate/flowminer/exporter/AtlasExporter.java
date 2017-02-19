package edu.iastate.flowminer.exporter;

import static com.ensoftcorp.atlas.core.script.Common.resolve;
import static com.ensoftcorp.atlas.core.script.Common.toQ;
import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.NodeGraph;
import com.ensoftcorp.atlas.core.db.graph.operation.ForwardGraph;
import com.ensoftcorp.atlas.core.db.graph.operation.ForwardStepGraph;
import com.ensoftcorp.atlas.core.db.graph.operation.ReverseGraph;
import com.ensoftcorp.atlas.core.db.notification.NotificationSet;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.db.set.IntersectionSet;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

import edu.iastate.flowminer.io.model.AnnotationTypeElement;
import edu.iastate.flowminer.io.model.Attribute;
import edu.iastate.flowminer.io.model.ClassTypeElement;
import edu.iastate.flowminer.io.model.ConstructorElement;
import edu.iastate.flowminer.io.model.Element;
import edu.iastate.flowminer.io.model.EnumConstantElement;
import edu.iastate.flowminer.io.model.EnumTypeElement;
import edu.iastate.flowminer.io.model.FieldVarElement;
import edu.iastate.flowminer.io.model.IOModel;
import edu.iastate.flowminer.io.model.InterfaceTypeElement;
import edu.iastate.flowminer.io.model.LibraryElement;
import edu.iastate.flowminer.io.model.MethodElement;
import edu.iastate.flowminer.io.model.NonPrimitiveTypeElement;
import edu.iastate.flowminer.io.model.PackageElement;
import edu.iastate.flowminer.io.model.ParamVarElement;
import edu.iastate.flowminer.io.model.PrimitiveTypeElement;
import edu.iastate.flowminer.io.model.ReturnVarElement;
import edu.iastate.flowminer.io.model.ThisVarElement;
import edu.iastate.flowminer.io.model.VarElement;
import edu.iastate.flowminer.log.Log;
import edu.iastate.flowminer.schema.ISUSchema;
import edu.iastate.flowminer.utility.ThreadPool;
import net.ontopia.utils.CompactHashMap;

public class AtlasExporter extends Exporter{	
	private Q containsContext = resolve(null, universe().edgesTaggedWithAny(XCSG.Contains));
	private Q typelessContainsContext = resolve(null, containsContext.differenceEdges(containsContext.reverseStep(universe().nodesTaggedWithAny(XCSG.Type))));
	private Q typeofContext = resolve(null, universe().edgesTaggedWithAny(XCSG.TypeOf));
	private Q arrayElementTypeContext = resolve(null, universe().edgesTaggedWithAny(XCSG.ArrayElementType));
	private Q overridesContext = resolve(null, universe().edgesTaggedWithAny(XCSG.Overrides));
	private Q supertypeContext = resolve(null, universe().edgesTaggedWithAny(XCSG.Supertype));
	private Q typeNodes = resolve(null, universe().nodesTaggedWithAny(XCSG.Type));
	private Node voidType = universe().nodesTaggedWithAny(XCSG.Void).eval().nodes().getFirst();
	private Map<Node, Element> exported;
	
	@Override
	protected void exportSummary(IProgressMonitor mon,
			Map<Node, Element> exported, IOModel model, Q toExport) throws Throwable {
		if(mon.isCanceled()) return;
		SubMonitor sm = SubMonitor.convert(mon, 124486);
		try{
			Log.info("AtlasExporter exporting");
			this.exported = exported;
			
			if(sm.isCanceled()) return;
			Log.info("AtlasExporter enumerating graph to export");
			Graph actualExportG = resolve(sm.newChild(936), toExport.eval());
			AtlasSet<Node> nodesToExport = new AtlasHashSet<Node>(actualExportG.nodes());

			LinkedList<Future<?>> futures = new LinkedList<Future<?>>();
			
			// Create DOMConvertables for all of the nodes and edges
			if(sm.isCanceled()) return;
			Log.info("AtlasExporter converting nodes and edges to model elements");
			if(sm.isCanceled()) return;
			SubMonitor sm2 = SubMonitor.convert(sm.newChild(9278), (int) (nodesToExport.size()));
			try{
				convertNodesToElements(sm2, futures, nodesToExport);
				ThreadPool.blockUntilAllComplete(true, futures);
			}finally{
				sm2.done();
			}

			futures.clear();
			
			// Internally nest the items under the model
			if(sm.isCanceled()) return;
			Log.info("AtlasExporter nesting model elements and adding structural references");
			SubMonitor sm3 = SubMonitor.convert(sm.newChild(33549), 100);
			try{
				nestElements(sm3.newChild(25), futures, model, nodesToExport);
				addStructuralReferences(sm3.newChild(25), futures, nodesToExport);
				ThreadPool.blockUntilAllComplete(true, futures);
			}finally{
				sm3.done();
			}
		}finally{
			sm.done();
		}
	}

	@Override
	protected Q contributeExports(IProgressMonitor mon, Q contributeFor) {
		if(mon.isCanceled()) return null;
		Log.info("AtlasExporter contributing exports");

		SubMonitor sm = SubMonitor.convert(mon, 10);
		int worked = 0;
		try{
			AtlasSet<Node> supportedNodes = new AtlasHashSet<Node>(contributeFor.eval().nodes());
			
			if(sm.isCanceled()) return null;
			Graph supertypeContextG = supertypeContext.eval();
			Graph containsContextG = containsContext.eval();
			Graph typelessDecContextG = typelessContainsContext.eval();
			Graph typeofContextG = typeofContext.eval();
			Graph arrayElementTypeContextG = arrayElementTypeContext.eval();
			Graph overridesContextG = overridesContext.eval();
			
			boolean changedThisRound = true;
			while(changedThisRound){
				if(sm.isCanceled()) return null;
				changedThisRound = false;
				
				// Newly-declaring packages, types, methods, projects, and libraries
				if(sm.isCanceled()) return null;
				AtlasSet<Node> newDeclaring = new ReverseGraph(containsContextG, supportedNodes).nodes().taggedWithAny(
						XCSG.Project, XCSG.Library, XCSG.Package, XCSG.Type, XCSG.Method);
				changedThisRound |= supportedNodes.addAll(newDeclaring);
				
				// Newly-declared signature elements
				if(sm.isCanceled()) return null;
				AtlasSet<Node> newDecs = new ForwardStepGraph(typelessDecContextG, supportedNodes).nodes().
						taggedWithAny(XCSG.CallInput, XCSG.ReturnValue);
				changedThisRound |= supportedNodes.addAll(newDecs);
				
				// New types of
				if(sm.isCanceled()) return null;
				AtlasSet<Node> newTypesOf = new ForwardStepGraph(typeofContextG, supportedNodes).nodes();
				changedThisRound |= supportedNodes.addAll(newTypesOf);
				
				// New element types
				if(sm.isCanceled()) return null;
				AtlasSet<Node> newElementtypes = new ForwardStepGraph(arrayElementTypeContextG, supportedNodes).nodes();
				changedThisRound |= supportedNodes.addAll(newElementtypes);
				
				// New supertypes
				if(sm.isCanceled()) return null;
				AtlasSet<Node> newSupertypes = new ForwardGraph(supertypeContextG, supportedNodes).nodes();
				changedThisRound |= supportedNodes.addAll(newSupertypes);
				
				// New overrides
				if(sm.isCanceled()) return null;
				AtlasSet<Node> newOverrides = new ForwardGraph(overridesContextG, supportedNodes).nodes();
				changedThisRound |= supportedNodes.addAll(newOverrides);
				
				if(worked < 10) {
					worked++;
					sm.worked(1);
				}
			}
			
			if(sm.isCanceled()) return null;
			return toQ(new NodeGraph(supportedNodes));
		}finally{
			sm.done();
		}
	}

	private void convertNodesToElements(final IProgressMonitor mon, List<Future<?>> futures, final AtlasSet<Node> nodes){
		/*
		 * Create libraries
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Project, XCSG.Library));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				Element e = new LibraryElement((String) ge.attr().get(XCSG.name));
				addTags(e, ge);
				localExported.put(ge, e);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);	
		
		/*
		 * Create packages
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Package));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				Element e = new PackageElement((String) ge.attr().get(XCSG.name));
				addTags(e, ge);
				localExported.put(ge, e);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);	
		
		/*
		 * Create primitive types
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Primitive, Attr.Node.NULL_TYPE, XCSG.Void));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				Element e = new PrimitiveTypeElement((String) ge.attr().get(XCSG.name));
				addTags(e, ge);
				localExported.put(ge, e);
				mon.worked(1);
			}
			
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);	
		
		/*
		 * Create classes
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Java.Class));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				Element e = ge.taggedWith(XCSG.Java.Enum) ? 
					new EnumTypeElement((String) ge.attr().get(XCSG.name)) : 
					new ClassTypeElement((String) ge.attr().get(XCSG.name));
				addTags(e, ge);
				localExported.put(ge, e);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);	
		
		/*
		 * Create interfaces
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Java.Interface));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				Element e = new InterfaceTypeElement((String) ge.attr().get(XCSG.name));
				addTags(e, ge);
				localExported.put(ge, e);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);
		
		/*
		 * Create annotations
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Java.Annotation));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				Element e = new AnnotationTypeElement((String) ge.attr().get(XCSG.name));
				addTags(e, ge);
				localExported.put(ge, e);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);
		
		/*
		 * Create context this
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Identity));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				ThisVarElement e = new ThisVarElement(null);
				addTags(e, ge);
					
				localExported.put(ge, e);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);
		
		/*
		 * Create returns
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.ReturnValue));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				Element e = new ReturnVarElement(null);
				addTags(e, ge);
				localExported.put(ge, e);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);
		
		/*
		 * Create params
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Parameter));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				ParamVarElement pe = new ParamVarElement(null);
				addTags(pe, ge);
				pe.setParam_idx((int) ge.attr().get(XCSG.parameterIndex));
				localExported.put(ge, pe);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);
		
		/*
		 * Create fields
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Field));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				FieldVarElement e = new FieldVarElement((String) ge.attr().get(XCSG.name));
				addTags(e, ge);
				localExported.put(ge, e);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);
		
		/*
		 * Create enum constants
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Java.EnumConstant));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				Element e = new EnumConstantElement((String) ge.attr().get(XCSG.name));
				addTags(e, ge);
				localExported.put(ge, e);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);
		
		/*
		 * Create methods
		 */
		if(mon.isCanceled()) return;
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			if(mon.isCanceled()) return;
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(nodes.taggedWithAny(XCSG.Method));
			Map<Node, Element> localExported = new CompactHashMap<Node, Element>((int) (workSet.size()*2));
			
			for(Node ge : workSet){
				MethodElement e = ge.tags().contains(XCSG.Constructor) ?
					new ConstructorElement((String) ge.attr().get(XCSG.name)): 
					new MethodElement((String) ge.attr().get(XCSG.name));

				addTags(e, ge);
				String sigString = (String) ge.getAttr("##signature");
				if(sigString != null) e.getAttr().add(new Attribute(ISUSchema.METHOD_SIGNATURE_STRING, sigString));
				localExported.put(ge, e);
				mon.worked(1);
			}
			synchronized(exported){
				for(Node ge : workSet) exported.put(ge, localExported.get(ge));
			}
		}})[0]);
	}
	
	private void nestElements(IProgressMonitor mon, List<Future<?>> futures, final IOModel model, final AtlasSet<Node> nodesToExport){
		if(mon.isCanceled()) return;
		final SubMonitor sm = SubMonitor.convert(mon, exported.keySet().size());
		try{
			final Graph decG = containsContext.eval();
			
			/*
			 *  Nest non-primitive, non-array types under packages, types, and methods
			 */
			AtlasSet<Node> types = nodesToExport.taggedWithAny(XCSG.Type);
			final AtlasSet<Node> local = types.taggedWithAny(XCSG.Java.LocalClass);
			final AtlasSet<Node> nested = new IntersectionSet<Node>(
					containsContext.successors(typeNodes).nodesTaggedWithAny(XCSG.Type).eval().nodes(),
					types);
			
			/*
			 * Nest top-level classes under packages
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nodesToExport.taggedWithAll(Attr.Node.IS_TOP_LEVEL, XCSG.Java.Class))){
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					PackageElement pe = (PackageElement) exported.get(ge2);
					NonPrimitiveTypeElement cte = (NonPrimitiveTypeElement) exported.get(ge);
					if(cte instanceof ClassTypeElement){
						pe.getType_class().add((ClassTypeElement) cte);
					}else{
						pe.getType_enum().add((EnumTypeElement) cte);
					}
					sm.worked(1);
				}
			}})[0]);

			/*
			 * Nest top-level interfaces under packages
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nodesToExport.taggedWithAll(Attr.Node.IS_TOP_LEVEL, XCSG.Java.Interface))){
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					PackageElement pe = (PackageElement) exported.get(ge2);
					pe.getType_interface().add((InterfaceTypeElement)exported.get(ge));
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest top-level annotations under packages
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nodesToExport.taggedWithAll(Attr.Node.IS_TOP_LEVEL, XCSG.Java.Annotation))){
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					PackageElement pe = (PackageElement) exported.get(ge2);
					pe.getType_annotation().add((AnnotationTypeElement)exported.get(ge));
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest methods under types
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nodesToExport.taggedWithAny(XCSG.Method))){
					MethodElement me = (MethodElement) exported.get(ge);
					Edge decEdge = decG.edges(ge, NodeDirection.IN).getFirst();

					Node ge2 = decEdge.getNode(EdgeDirection.FROM);
					NonPrimitiveTypeElement te = (NonPrimitiveTypeElement) exported.get(ge2);
					if(ge.tags().contains(XCSG.Constructor)){
						te.getConstructor().add((ConstructorElement) me);
					}else{
						te.getMethod().add(me);
					}
					sm.worked(1);
				}
			}})[0]);
			
			final AtlasSet<Node> variables = nodesToExport.taggedWithAny(XCSG.Variable);
			
			/*
			 * Nest nested classes under types 
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nested.taggedWithAny(XCSG.Java.Class))){
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					NonPrimitiveTypeElement te2 = (NonPrimitiveTypeElement) exported.get(ge2);
					NonPrimitiveTypeElement cte = (NonPrimitiveTypeElement) exported.get(ge);
					if(cte instanceof ClassTypeElement){
						te2.getNestedClass().add((ClassTypeElement) cte);
					}else{
						te2.getNestedEnum().add((EnumTypeElement) cte);
					}
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest nested interfaces under types 
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nested.taggedWithAny(XCSG.Java.Interface))){
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					NonPrimitiveTypeElement te2 = (NonPrimitiveTypeElement) exported.get(ge2);
					te2.getNestedInterface().add((InterfaceTypeElement)exported.get(ge));
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest nested annotations under types 
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nested.taggedWithAny(XCSG.Java.Annotation))){
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					NonPrimitiveTypeElement te2 = (NonPrimitiveTypeElement) exported.get(ge2);
					te2.getNestedAnnotation().add((AnnotationTypeElement)exported.get(ge));	
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest fields under types
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(variables.taggedWithAny(XCSG.Field))){
					FieldVarElement fe = (FieldVarElement) exported.get(ge);
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					NonPrimitiveTypeElement te = (NonPrimitiveTypeElement) exported.get(ge2);
					te.getField().add(fe);
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest enum constants under types
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(variables.taggedWithAny(XCSG.Java.EnumConstant))){
					EnumConstantElement ee = (EnumConstantElement) exported.get(ge);
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					EnumTypeElement te = (EnumTypeElement) exported.get(ge2);
					te.getEnumConstant().add(ee);
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest params under methods
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(variables.taggedWithAny(XCSG.Parameter))){
					ParamVarElement pe = (ParamVarElement) exported.get(ge);
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					MethodElement me = (MethodElement) exported.get(ge2);
					me.getParam().add(pe);
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest context this under methods
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nodesToExport.taggedWithAny(XCSG.Identity))){
					ThisVarElement tve = (ThisVarElement) exported.get(ge);
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					MethodElement me = (MethodElement) exported.get(ge2);
					me.setContextThis(tve);
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest local classes under methods
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(local.taggedWithAny(XCSG.Java.Class))){
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					MethodElement me = (MethodElement) exported.get(ge2);
					NonPrimitiveTypeElement cte = (NonPrimitiveTypeElement) exported.get(ge);
					if(cte instanceof ClassTypeElement){
						me.getLocal_class().add((ClassTypeElement) cte);
					}else{
						me.getLocal_enum().add((EnumTypeElement) cte);
					}
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest local interfaces under methods
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(local.taggedWithAny(XCSG.Java.Interface))){
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					MethodElement me = (MethodElement) exported.get(ge2);
					me.getLocal_interface().add((InterfaceTypeElement)exported.get(ge));
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest local enums under methods
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(local.taggedWithAny(XCSG.Java.Enum))){
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					MethodElement me = (MethodElement) exported.get(ge2);
					me.getLocal_enum().add((EnumTypeElement)exported.get(ge));
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest local annotations under methods
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(local.taggedWithAny(XCSG.Java.Annotation))){
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					MethodElement me = (MethodElement) exported.get(ge2);
					me.getLocal_annotation().add((AnnotationTypeElement)exported.get(ge));
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest returns under methods
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nodesToExport.taggedWithAny(XCSG.ReturnValue))){
					ReturnVarElement re = (ReturnVarElement) exported.get(ge);
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					MethodElement me = (MethodElement) exported.get(ge2);
					me.setReturned(re);
					sm.worked(1);
				}
			}})[0]);

			/*
			 *  Nest primitive types under IOModel
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nodesToExport.taggedWithAny(XCSG.Void, Attr.Node.NULL_TYPE, XCSG.Primitive))){
					model.getPrimitive().add((PrimitiveTypeElement) exported.get(ge));
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest packages under libraries
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nodesToExport.taggedWithAny(XCSG.Package))){
					PackageElement pe = (PackageElement) exported.get(ge);
					Node ge2 = decG.edges(ge, NodeDirection.IN).getFirst().getNode(EdgeDirection.FROM);
					LibraryElement le = (LibraryElement) exported.get(ge2);
					le.getPackages().add(pe);
					sm.worked(1);
				}
			}})[0]);
			
			/*
			 * Nest libraries under IOModel
			 */
			if(sm.isCanceled()) return;
			futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
				if(sm.isCanceled()) return;
				for(Node ge : new AtlasHashSet<Node>(nodesToExport.taggedWithAny(XCSG.Library, XCSG.Project))){
					model.getLibrary().add((LibraryElement) exported.get(ge));
					sm.worked(1);
				}
			}})[0]);
		}finally{
			sm.done();
		}
	}
	
	private void addStructuralReferences(final IProgressMonitor mon, List<Future<?>> futures, final AtlasSet<Node> nodesToExport){
		if(mon.isCanceled()) return;
		
		futures.add(ThreadPool.submitRunnables(new Runnable(){public void run() {
			AtlasSet<Node> workSet = new AtlasHashSet<Node>(
					nodesToExport.taggedWithAny(XCSG.Variable, XCSG.ReturnValue, XCSG.Method, XCSG.Type));
			SubMonitor sm = SubMonitor.convert(mon, (int) workSet.size());
			try{
				Graph typeofG = typeofContext.eval();
				Graph elementG = arrayElementTypeContext.eval();
				Graph overridesG = overridesContext.eval();
				Graph supertypeG = supertypeContext.eval();
				
				for(Node ge : workSet){
					if(sm.isCanceled()) return;
					NotificationSet<String> geTags = ge.tags();
					
					if(geTags.contains(XCSG.Variable) || geTags.contains(XCSG.ReturnValue)){
						Node type = typeofG.edges(ge, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO);
						VarElement ve = (VarElement) exported.get(ge);
						
						if(type.tags().contains(XCSG.ArrayType)){
							ve.setArray_dim((int) type.attr().get(Attr.Node.DIMENSION));
							type = elementG.edges(type, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO);
						}
						try{
							if(type != voidType) ve.setType(exported.get(type).getId());
						}catch(NullPointerException npe){}
					}
					else if(geTags.contains(XCSG.Method)){
						for(Edge overridesEdge : overridesG.edges(ge, NodeDirection.OUT)){
							MethodElement me = (MethodElement) exported.get(ge);
							Node overridden = overridesEdge.getNode(EdgeDirection.TO);
							MethodElement overriddenElement = (MethodElement) exported.get(overridden);
							me.getOverrides().add(overriddenElement.getId());
						}
					}
					else if(geTags.contains(XCSG.Java.Class) || geTags.contains(XCSG.Java.Interface) || geTags.contains(XCSG.Java.Annotation)){
						NonPrimitiveTypeElement te = (NonPrimitiveTypeElement) exported.get(ge);
						
						for(Edge supertypeEdge : supertypeG.edges(ge, NodeDirection.OUT)){
							Node ancestor = supertypeEdge.getNode(EdgeDirection.TO);
							// We do not export array types explicitly
							if(ancestor.taggedWith(XCSG.ArrayType)) continue;
							NonPrimitiveTypeElement ancestorElement = (NonPrimitiveTypeElement) exported.get(ancestor);
							
							if (ancestorElement == null) {
								Log.warning("Missing dependency, reference to type which was not exported: " + ancestor.toString(), new RuntimeException());
								continue;
							}
							
							if(ancestor.taggedWith(XCSG.Java.Class)){
								te.setExtend(ancestorElement.getId());
							}else {
								te.getImplement().add(ancestorElement.getId());
							}
						}
					}
					sm.worked(1);
				}
			}finally{
				sm.done();
			}
		}})[0]);
	}
}
