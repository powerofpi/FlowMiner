package edu.iastate.flowminer.importer;

import static com.ensoftcorp.atlas.core.script.Common.typeSelect;
import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import net.ontopia.utils.CompactHashMap;
import net.ontopia.utils.CompactHashSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.osgi.framework.Version;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.java.core.script.Common;

import edu.iastate.flowminer.exception.FlowMinerException;
import edu.iastate.flowminer.io.Schema;
import edu.iastate.flowminer.io.StreamAPI;
import edu.iastate.flowminer.io.StreamAPI.StreamImporter.Param;
import edu.iastate.flowminer.log.Log;
import edu.iastate.flowminer.schema.ISUSchema;
import edu.iastate.flowminer.schema.SummarySchema;

public abstract class Importer {
	protected GraphElement object, cloneable, serializable;
	private AtlasSet<GraphElement> arrayAncestorSet;
	protected static final Set<String> ARRAY_TYPE_NODE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> ELEMENTTYPE_EDGE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> DECLARES_EDGE_TAGS = new CompactHashSet<String>();
	protected static final Map<String, Object> ATTR = new CompactHashMap<String, Object>(5);
	protected static final Set<String> SUPERTYPE_EDGE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> EXTENDS_EDGE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> IMPLEMENTS_EDGE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> RETURNS_EDGE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> OVERRIDES_EDGE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> PARAM_EDGE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> TYPEOF_EDGE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> PARAM_NODE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> RETURN_NODE_TAGS = new CompactHashSet<String>();
	protected static final Set<String> THIS_NODE_TAGS = new CompactHashSet<String>();
	
	static{
		SUPERTYPE_EDGE_TAGS.add(XCSG.Language.Java);
		SUPERTYPE_EDGE_TAGS.add(XCSG.Supertype);
		
		EXTENDS_EDGE_TAGS.addAll(SUPERTYPE_EDGE_TAGS);
		EXTENDS_EDGE_TAGS.add(XCSG.Java.Extends);
		
		IMPLEMENTS_EDGE_TAGS.addAll(SUPERTYPE_EDGE_TAGS);
		IMPLEMENTS_EDGE_TAGS.add(XCSG.Java.Implements);
		
		RETURNS_EDGE_TAGS.add(XCSG.Language.Java);
		RETURNS_EDGE_TAGS.add(XCSG.Returns);
		
		OVERRIDES_EDGE_TAGS.add(XCSG.Language.Java);
		OVERRIDES_EDGE_TAGS.add(XCSG.Overrides);
		
		PARAM_EDGE_TAGS.add(XCSG.Language.Java);
		PARAM_EDGE_TAGS.add(XCSG.HasParameter);
		
		TYPEOF_EDGE_TAGS.add(XCSG.Language.Java);
		TYPEOF_EDGE_TAGS.add(XCSG.TypeOf);
		
		ARRAY_TYPE_NODE_TAGS.add(XCSG.Language.Java);
		ARRAY_TYPE_NODE_TAGS.add(XCSG.Type);
		ARRAY_TYPE_NODE_TAGS.add(XCSG.ArrayType);
		
		ELEMENTTYPE_EDGE_TAGS.add(XCSG.Language.Java);
		ELEMENTTYPE_EDGE_TAGS.add(XCSG.ArrayElementType);
		
		DECLARES_EDGE_TAGS.add(XCSG.Language.Java);
		DECLARES_EDGE_TAGS.add(XCSG.Contains);
		
		PARAM_NODE_TAGS.add(XCSG.Language.Java);
		PARAM_NODE_TAGS.add(XCSG.Variable);
		PARAM_NODE_TAGS.add(XCSG.Parameter);
		
		RETURN_NODE_TAGS.add(XCSG.Language.Java);
		RETURN_NODE_TAGS.add(XCSG.DataFlow_Node);
		RETURN_NODE_TAGS.add(XCSG.ReturnValue);
		
		THIS_NODE_TAGS.add(XCSG.Language.Java);
		THIS_NODE_TAGS.add(XCSG.Variable);
		THIS_NODE_TAGS.add(XCSG.Identity);
	}
	
	public Importer(boolean existingIndex){
		arrayAncestorSet = new AtlasHashSet<GraphElement>();
		
		if(existingIndex){
			object = typeSelect("java.lang","Object").eval().nodes().getFirst();
			if(object == null){
				Log.warning("Didn't find existing java.lang.Object");
			}else{
				arrayAncestorSet.add(object);
			}

			cloneable = typeSelect("java.lang","Cloneable").eval().nodes().getFirst();
			if(cloneable == null) {
				Log.warning("Didn't find existing java.lang.Cloneable interface");
			}else{
				arrayAncestorSet.add(cloneable);
			}
			
			serializable = typeSelect("java.io","Serializable").eval().nodes().getFirst();
			if(serializable == null){
				Log.warning("Didn't find existing java.io.Serializable interface");
			}else{
				arrayAncestorSet.add(serializable);
			}
		}else{
			object = null;
			cloneable = null;
			serializable = null;
		}
	}
	
	/**
	 * Import the requested summary schema from its compressed XML file, applying the summaries
	 * as nodes and edges to your index. 
	 * 
	 * @param mon
	 * @param schema
	 * @return
	 * @throws Throwable
	 */
	public static Q importSummary(final SummarySchema schema, final boolean existingIndex, final String path) throws Throwable{
		return importSummary(null, existingIndex, schema, path);
	}
	
	/**
	 * Import the requested summary schema from its compressed XML file, applying the summaries
	 * as nodes and edges to your index. 
	 * 
	 * @param mon
	 * @param schema
	 * @return
	 * @throws Throwable
	 */
	public static Q importSummary(final IProgressMonitor mon, final boolean existingIndex, final SummarySchema schema, final String path) throws Throwable{
		final Q[] result = new Q[1];
		final String what = schema.getClass().getSimpleName() + ": " + path;
		
		class JobImporter{
			IProgressMonitor monitor;
			public JobImporter(IProgressMonitor monitor){
				this.monitor = monitor;
			}
			
			IStatus doImport(){
				SubMonitor sm = SubMonitor.convert(monitor);
				try{
					Log.info("Importing " + what);
					
					sm.setTaskName("Instantiating importer");
					String file = null;
					Importer schemaImporter = null;
					file = path;
					if(schema != null)
						schemaImporter = schema.getImporter(existingIndex);				
					
					Importer atlasImporter = new AtlasImporter(existingIndex);
					
					sm.setTaskName("Streaming graph schema from XML into Index");
					StreamImporter si = new StreamImporter(atlasImporter, schemaImporter);
					edu.iastate.flowminer.io.StreamAPI.importFromFile(sm, file, si);
					
					sm.setTaskName("Collecting result");
					AtlasSet<GraphElement> importedNodes = new AtlasHashSet<GraphElement>();
					AtlasSet<GraphElement> importedEdges = new AtlasHashSet<GraphElement>();
					for(GraphElement ge : si.imported.values()){
						if(Graph.U.nodes().contains(ge)) importedNodes.add(ge);
						if(Graph.U.edges().contains(ge)){
							importedEdges.add(ge);
							importedNodes.add(ge.getNode(EdgeDirection.TO));
							importedNodes.add(ge.getNode(EdgeDirection.FROM));
						}
					}
					result[0] = Common.toQ(new UncheckedGraph(importedNodes, importedEdges));
		
					Log.info("Import finished");
					return Status.OK_STATUS;
				}catch(Throwable t){
					return new Status(Status.ERROR, Log.pluginid,"Exception thrown during import",t);
				}finally{
					sm.done();
				}
			}
		}
		
		if(mon == null){
			Job job = new Job("Importing " + what){
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
		
		return result[0];
	}

	/**
	 * Cache of array base + dimension to array type in index, if it exists. Null value means it does not yet exist.
	 */
	private static Map<ArrayElement, GraphElement> arrayTypeCache = new CompactHashMap<ArrayElement, GraphElement>();
	
	/**
	 * Looks up the requested array type, checking the cache first.
	 * @param base
	 * @param dimension
	 * @return
	 */
	private static GraphElement getArrayType(GraphElement base, int dimension){
		ArrayElement cacheKey = new ArrayElement(base, dimension);
		if(arrayTypeCache.containsKey(cacheKey)){
			return arrayTypeCache.get(cacheKey);
		}

		// Return the existing array type if it already exists
		for(GraphElement eTypeEdge : universe().edgesTaggedWithAny(XCSG.ArrayElementType).eval().edges(base, NodeDirection.IN)){
			GraphElement candidate = eTypeEdge.getNode(EdgeDirection.FROM);
			if(((Integer) candidate.getAttr(Attr.Node.DIMENSION)) == dimension){
				arrayTypeCache.put(cacheKey, candidate);
				return candidate;
			}
		}
		
		arrayTypeCache.put(cacheKey, null);
		return null;
	}
	
	/**
	 * Inserts the given type into the index if it does not yet exist
	 * @param base
	 * @param arrayDimension
	 * @return
	 */
	private GraphElement getOrCreateArrayType(GraphElement requester, GraphElement baseType, int arrayDimension){
		Q u = universe();

		// Return the existing array type if it already exists
		GraphElement arrayType = getArrayType(baseType, arrayDimension);
		if(arrayType != null) return arrayType;
		
		// Create the new array type
		StringBuilder sb = new StringBuilder();
		sb.append(baseType.getAttr(XCSG.name));
		for(int i=0; i<arrayDimension; i++) sb.append("[]");
		ATTR.clear();
		ATTR.put(XCSG.name, sb.toString());
		ATTR.put(Attr.Node.DIMENSION, arrayDimension);
		arrayType = createNode(null, ARRAY_TYPE_NODE_TAGS, ATTR);
		arrayTypeCache.put(new ArrayElement(baseType, arrayDimension), arrayType);
		
		// Extra typing relationships if base type is not a primitive
		if(!baseType.taggedWith(XCSG.Primitive)){
			// Create Supertype edges to all compatible array types of the same dimension 
			for(GraphElement supertypeEdge : u.edgesTaggedWithAny(XCSG.Supertype).eval().edges(baseType, NodeDirection.OUT)){
				GraphElement other = supertypeEdge.getNode(EdgeDirection.TO);
				if(other != requester){
					// java.lang.Object is the supertype, always create the array type if it doesn't exist yet.
					// Else, only if it exists already
					GraphElement otherType = other == object ? 
							getOrCreateArrayType(arrayType, other, arrayDimension):
							getArrayType(other, arrayDimension);
					if(otherType != null){
						ATTR.clear();
						ATTR.put(XCSG.name, XCSG.Supertype);
						createEdge(arrayType, otherType, SUPERTYPE_EDGE_TAGS, ATTR);	
					}
				}
			}
			
			// Create Supertype edges from all compatible existing array types of the same dimension
			for(GraphElement subtypeEdge : u.edgesTaggedWithAny(XCSG.Supertype).eval().edges(baseType, NodeDirection.IN)){
				GraphElement other = subtypeEdge.getNode(EdgeDirection.FROM);
				if(other != requester){
					GraphElement otherArrayType = getArrayType(other, arrayDimension);
					if(otherArrayType != null){
						ATTR.clear();
						ATTR.put(XCSG.name, XCSG.Supertype);
						createEdge(otherArrayType, arrayType, SUPERTYPE_EDGE_TAGS, ATTR);
						break;
					}
				}
			}
		}
		
		// Array types (even primitive array types) are always subtypes of Object[], Serializable[], Cloneable[] of dimension-1
		if(baseType.taggedWith(XCSG.Primitive) || baseType == object){
			if(arrayDimension > 1){
				for(GraphElement ancestor : arrayAncestorSet){
					GraphElement otherType = getOrCreateArrayType(arrayType, ancestor, arrayDimension - 1);
					ATTR.clear();
					ATTR.put(XCSG.name, XCSG.Supertype);
					createEdge(arrayType, otherType, SUPERTYPE_EDGE_TAGS, ATTR);
				}
			}else{
				for(GraphElement ancestor : arrayAncestorSet){
					ATTR.clear();
					ATTR.put(XCSG.name, XCSG.Supertype);
					createEdge(arrayType, ancestor, SUPERTYPE_EDGE_TAGS, ATTR);
				}
			}
		}

		return arrayType;
	}
	
	protected GraphElement findType(Map<Long, GraphElement> imported, long typeID, int arrayDim){
		GraphElement base = imported.get(typeID);
		if(arrayDim < 1) return base;
		return getOrCreateArrayType(null, base, arrayDim);
	}

	protected void addTypeof(Map<Long, GraphElement> imported, GraphElement var, long typeID, int arrayDim){
		GraphElement type = findType(imported, typeID, arrayDim);
		
		ATTR.clear();
		ATTR.put(XCSG.name, XCSG.TypeOf);
		createEdge(var, type, TYPEOF_EDGE_TAGS, ATTR);
	}
	
	protected static GraphElement createNode(GraphElement parent, Set<String> tags, Map<String,Object> attr){	
		GraphElement newNode = Graph.U.createNode();

		for(String s : tags) newNode.tag(s);	
		for(String key : attr.keySet()) newNode.putAttr(key, attr.get(key));
		
		if(parent != null){
			ATTR.clear();
			ATTR.put(XCSG.name, XCSG.Contains);
			createEdge(parent, newNode, DECLARES_EDGE_TAGS, ATTR);
		}
		
		return newNode;
	}
	
	protected static GraphElement createTranslatedNode(GraphElement parent, String kind, Set<String> tags, Map<String,String> attr){	
		GraphElement newNode = Graph.U.createNode();
		
		/*NotificationSet<String> geTags = ge.tags();
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
		if(geTags.contains(XCSG.volatileVariable)) tags.add(Schema.Tag.IS_VOLATILE);*/
		
		for(String s : tags) {
			switch(s){
			case Schema.Tag.IS_PUBLIC:
				newNode.tag(XCSG.publicVisibility);
				break;
			case Schema.Tag.IS_PROTECTED:
				newNode.tag(XCSG.protectedPackageVisibility);
				break;
			case Schema.Tag.IS_PRIVATE:
				newNode.tag(XCSG.privateVisibility);
				break;
			case Schema.Tag.IS_ABSTRACT:
				newNode.tag(Attr.Node.IS_ABSTRACT);
				if(kind.equals(XCSG.Method)){
					newNode.tag(XCSG.abstractMethod);
				}else if(kind.equals(XCSG.Type)){
					newNode.tag(XCSG.Java.AbstractClass);
				}
				break;
			case Schema.Tag.IS_FINAL:
				newNode.tag(Attr.Node.IS_FINAL);
				if(kind.equals(XCSG.Method)){
					// Missing in XCSG
				}else if(kind.equals(XCSG.Type)){
					newNode.tag(XCSG.Java.finalClass);
				}else if(kind.equals(XCSG.Variable)){
					newNode.tag(XCSG.immutable);
				}
				break;
			case Schema.Tag.IS_NATIVE:
				newNode.tag(XCSG.Java.nativeMethod);
				break;
			case Schema.Tag.IS_STATIC:
				newNode.tag(Attr.Node.IS_STATIC);
				if(kind.equals(XCSG.Variable)){
					newNode.tag(XCSG.ClassVariable);
				}else if(kind.equals(XCSG.Method)){
					newNode.tag(XCSG.ClassMethod);
				}
				break;
			case Schema.Tag.IS_SYNCHRONIZED:
				newNode.tag(XCSG.Java.synchronizedMethod);
				break;
			case Schema.Tag.IS_STRICTFP:
				newNode.tag(Attr.Node.IS_STRICTFP);
				break;
			case Schema.Tag.IS_TRANSIENT:
				newNode.tag(XCSG.Java.transientField);
				break;
			case Schema.Tag.IS_VOLATILE:
				newNode.tag(XCSG.volatileVariable);
				break;
			default:
				newNode.tag(s);	
			}
		}
		
		for(String key : attr.keySet()){
			switch(key){
			case ISUSchema.METHOD_SIGNATURE_STRING:
				newNode.putAttr("##signature", attr.get(key));
				break;
			default:
				newNode.putAttr(key, attr.get(key));
			}
		}
		
		if(parent != null){
			ATTR.clear();
			ATTR.put(XCSG.name, XCSG.Contains);
			createEdge(parent, newNode, DECLARES_EDGE_TAGS, ATTR);
		}
		
		return newNode;
	}
	
	protected static GraphElement createBlankEdge(GraphElement from, GraphElement to){
		if(from == null){
			throw new FlowMinerException("Requested edge origin was null!");
		}else if(to == null){
			throw new FlowMinerException("Requested edge destination was null!");
		}
		return Graph.U.createEdge(from, to);
	}
	
	protected static GraphElement createEdge(GraphElement from, GraphElement to, Set<String> tags, Map<String,Object> attr){	
		GraphElement newEdge = createBlankEdge(from, to);
		
		for(String s : tags) newEdge.tag(s);	
		for(String key : attr.keySet()) newEdge.putAttr(key, attr.get(key));
		
		return newEdge;
	}
	
	protected abstract boolean processAnnotation(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long extendID, Set<Long> implementID);
	
	protected abstract boolean processClass(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long extendID, Set<Long> implementID);
	
	protected abstract boolean processConstructor(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr, Set<Param> params);
	
	protected abstract boolean processEnumConstant(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr, long type);
	
	protected abstract boolean processEnum(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long extendID, Set<Long> implementID);
	
	protected abstract boolean processField(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long type, int dimension);
	
	protected abstract boolean processInterface(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long extendID, Set<Long> implementID);
	
	protected abstract boolean processIOModel(Map<Long,GraphElement> imported, String author, Date created);
	
	protected abstract boolean processLocal(Map<Long,GraphElement> imported, String schemaType, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long type, int dimension, int paramIdx);
	
	protected abstract boolean processMethod(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			Set<Long> overridesID, Set<StreamAPI.StreamImporter.Param> params);
	
	protected abstract boolean processPackage(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr);
	
	protected abstract boolean processLibrary(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr);
	
	protected abstract boolean processPrimitive(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr);
	
	protected abstract boolean processRelationship(Map<Long,GraphElement> imported, String name, String type, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long originID, long destID);
	
	protected abstract boolean processThis(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long type);
	
	protected abstract boolean processReturn(Map<Long,GraphElement> imported, String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long type, int dimension);
	
	
	private static class StreamImporter implements StreamAPI.StreamImporter{
		Importer atlasImporter;
		Importer schemaImporter;
		private final Map<Long, GraphElement> imported = new CompactHashMap<Long, GraphElement>();
		
		public StreamImporter(Importer atlasImporter, Importer schemaImporter){
			this.atlasImporter = atlasImporter;
			this.schemaImporter = schemaImporter;
		}
		
		@Override
		public final void processAnnotation(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr,
				long extendID, Set<Long> implementID) {
			if(!atlasImporter.processAnnotation(imported, name, id, parentID, tag, attr, extendID, implementID) && 
					schemaImporter != null){
				schemaImporter.processAnnotation(imported, name, id, parentID, tag, attr, extendID, implementID);
			}
		}

		@Override
		public final void processClass(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr,
				long extendID, Set<Long> implementID) {
			if(!atlasImporter.processClass(imported, name, id, parentID, tag, attr, extendID, implementID) && 
					schemaImporter != null){
				schemaImporter.processClass(imported, name, id, parentID, tag, attr, extendID, implementID);
			}
		}

		@Override
		public final void processConstructor(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr, Set<Param> params) {
			if(!atlasImporter.processConstructor(imported, name, id, parentID, tag, attr, params) && schemaImporter != null){
				schemaImporter.processConstructor(imported, name, id, parentID, tag, attr, params);
			}
		}

		@Override
		public final void processEnumConstant(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr, long type) {
			if(!atlasImporter.processEnumConstant(imported, name, id, parentID, tag, attr, type) &&
					schemaImporter != null){
				schemaImporter.processEnumConstant(imported, name, id, parentID, tag, attr, type);
			}
		}

		@Override
		public final void processEnum(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr,
				long extendID, Set<Long> implementID) {
			if(!atlasImporter.processEnum(imported, name, id, parentID, tag, attr, extendID, implementID) &&
					schemaImporter != null){
				schemaImporter.processEnum(imported, name, id, parentID, tag, attr, extendID, implementID);
			}
		}

		@Override
		public final void processField(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr,
				long type, int dimension) {
			if(!atlasImporter.processField(imported, name, id, parentID, tag, attr, type, dimension) && 
					schemaImporter != null){
				schemaImporter.processField(imported, name, id, parentID, tag, attr, type, dimension);
			}
		}

		@Override
		public final void processInterface(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr,
				long extendID, Set<Long> implementID) {
			if(!atlasImporter.processInterface(imported, name, id, parentID, tag, attr, extendID, implementID) &&
					schemaImporter != null){
				schemaImporter.processInterface(imported, name, id, parentID, tag, attr, extendID, implementID);
			}
		}

		@Override
		public final void processIOModel(String author, Date created) {
			// Version check
			Version current = Platform.getBundle(Log.pluginid).getVersion();
			Version savedAt = Version.parseVersion(author);
			if(current.getMajor() != savedAt.getMajor() || current.getMinor() != savedAt.getMinor()){
				throw new FlowMinerException("FlowMiner is at version " + current + ", but summary file was exported with version " + savedAt);
			}
			if(!atlasImporter.processIOModel(imported, author, created) && schemaImporter != null){
				schemaImporter.processIOModel(imported, author, created);
			}
		}

		@Override
		public final void processLocal(String name, String schemaType, long id,
				long parentID, Set<String> tag, Map<String, String> attr,
				long type, int dimension, int paramIdx) {
			if(!atlasImporter.processLocal(imported, name, schemaType, id, parentID, tag, attr, type, dimension, paramIdx) &&
					schemaImporter != null){
				schemaImporter.processLocal(imported, name, schemaType, id, parentID, tag, attr, type, dimension, paramIdx);
			}
		}

		@Override
		public final void processMethod(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr,
				Set<Long> overridesID, Set<Param> params) {
			if(!atlasImporter.processMethod(imported, name, id, parentID, tag, attr, overridesID, params) &&
					schemaImporter != null){
				schemaImporter.processMethod(imported, name, id, parentID, tag, attr, overridesID, params);
			}
		}

		@Override
		public final void processPackage(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr) {
			if(!atlasImporter.processPackage(imported, name, id, parentID, tag, attr) && schemaImporter != null){
				schemaImporter.processPackage(imported, name, id, parentID, tag, attr);
			}
		}

		@Override
		public final void processPrimitive(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr) {
			if(!atlasImporter.processPrimitive(imported, name, id, parentID, tag, attr) && schemaImporter != null){
				schemaImporter.processPrimitive(imported, name, id, parentID, tag, attr);
			}
		}

		@Override
		public final void processRelationship(String name, String type, long id,
				long parentID, Set<String> tag, Map<String, String> attr,
				long originID, long destID) {
			if(!atlasImporter.processRelationship(imported, name, type, id, parentID, tag, attr, originID, destID) && 
					schemaImporter != null){
				schemaImporter.processRelationship(imported, name, type, id, parentID, tag, attr, originID, destID);
			}
		}

		@Override
		public void processThis(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr,
				long type) {
			if(!atlasImporter.processThis(imported, name, id, parentID, tag, attr, type) && 
					schemaImporter != null){
				schemaImporter.processThis(imported, name, id, parentID, tag, attr, type);
			}
		}

		@Override
		public void processReturn(String name, long id,
				long parentID, Set<String> tag, Map<String, String> attr,
				long type, int dimension) {
			if(!atlasImporter.processReturn(imported, name, id, parentID, tag, attr, type, dimension) && 
					schemaImporter != null){
				schemaImporter.processReturn(imported, name, id, parentID, tag, attr, type, dimension);
			}
		}

		@Override
		public void processLibrary(String name, long id, long parentID,
				Set<String> tag, Map<String, String> attr) {
			if(!atlasImporter.processLibrary(imported, name, id, parentID, tag, attr) && 
					schemaImporter != null){
				schemaImporter.processLibrary(imported, name, id, parentID, tag, attr);
			}
		}
	}
	
	private static class ArrayElement{
		private GraphElement base;
		private int dimension;
		
		public ArrayElement(GraphElement base, int dimension){
			this.base = base;
			this.dimension = dimension;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((base == null) ? 0 : base.hashCode());
			result = prime * result + dimension;
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
			ArrayElement other = (ArrayElement) obj;
			if (base == null) {
				if (other.base != null)
					return false;
			} else if (!base.equals(other.base))
				return false;
			if (dimension != other.dimension)
				return false;
			return true;
		}
	}
}
