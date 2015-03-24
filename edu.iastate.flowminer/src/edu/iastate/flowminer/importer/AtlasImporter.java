package edu.iastate.flowminer.importer;

import static com.ensoftcorp.atlas.core.script.Common.toGraph;
import static com.ensoftcorp.atlas.core.script.Common.toQ;
import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.db.set.IntersectionSet;
import com.ensoftcorp.atlas.core.query.Attr;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

import edu.iastate.flowminer.io.StreamAPI;
import edu.iastate.flowminer.io.StreamAPI.StreamImporter.Param;
import edu.iastate.flowminer.schema.ISUSchema;

public class AtlasImporter extends Importer{
	private static final String UNNAMED_THIS = "this";
	private static final String UNNAMED_RETURN = "return";
	private static final String UNNAMED_PARAM = "p";
		
	private final AtlasSet<GraphElement> decRoots = new AtlasHashSet<GraphElement>(universe().edgesTaggedWithAny(XCSG.Contains).
			roots().eval().nodes());

	public AtlasImporter(boolean existingIndex) {
		super(existingIndex);
	}
	
	private void extendsAndImplements(Map<Long, GraphElement> imported, GraphElement type, long extendID,
			Set<Long> implementID){
		if(extendID > StreamAPI.StreamImporter.NOT_DEFINED){
			Graph ec = universe().edgesTaggedWithAny(XCSG.Java.Extends).eval();
			ATTR.clear();
			ATTR.put(XCSG.name, XCSG.Java.Extends);
			addIfNotExists(type, imported.get(extendID), EXTENDS_EDGE_TAGS, ATTR, ec);
		}
		
		Graph ic = universe().edgesTaggedWithAny(XCSG.Java.Implements).eval();
		ATTR.clear();
		ATTR.put(XCSG.name, XCSG.Java.Implements);
		for(Long l : implementID){
			addIfNotExists(type, imported.get(l), IMPLEMENTS_EDGE_TAGS, ATTR, ic);
		}
		
		// If nothing extended or implemented, add supertype edge to Object
		Graph sc = universe().edgesTaggedWithAny(XCSG.Supertype).eval();
		if(extendID == StreamAPI.StreamImporter.NOT_DEFINED && implementID.isEmpty() && type != object){
			ATTR.clear();
			ATTR.put(XCSG.name, XCSG.Supertype);
			addIfNotExists(type, object, Importer.SUPERTYPE_EDGE_TAGS, ATTR, sc);
		}
	}
	
	private void addOverrides(Map<Long, GraphElement> imported, GraphElement method, Set<Long> overridesID){
		ATTR.clear();
		ATTR.put(XCSG.name, XCSG.Overrides);
		
		Graph ocg = universe().edgesTaggedWithAny(XCSG.Overrides).eval();
		for(Long l : overridesID){
			addIfNotExists(method, imported.get(l), OVERRIDES_EDGE_TAGS, ATTR, ocg);
		}
	}
	
	private void addIfNotExists(GraphElement from, GraphElement to, Set<String> tags, Map<String, Object> attr, Graph context){
		if(new IntersectionSet<GraphElement>(context.edges(from, NodeDirection.OUT), context.edges(to, NodeDirection.IN)).isEmpty()){
			createEdge(from, to, tags, attr);
		}
	}
	
	private void createParam(Map<Long, GraphElement> imported, GraphElement method, Param p){
		GraphElement match = createTranslatedNode(method, XCSG.Variable, p.tag, p.attr);	

		match.tag(XCSG.Variable);
		match.tag(XCSG.Parameter);
		match.putAttr(XCSG.name, p.name == null ? (UNNAMED_PARAM + p.paramIdx) : p.name);
		match.putAttr(XCSG.parameterIndex, p.paramIdx);
		
		imported.put(p.id, match);
		
		addTypeof(imported, match, p.typeID, p.arrayDimension);
		
		ATTR.clear();
		ATTR.put(XCSG.name, XCSG.HasParameter);
		createEdge(method, match, PARAM_EDGE_TAGS, ATTR);
	}
	
	private void createThis(Map<Long, GraphElement> imported, GraphElement method, String name,
			Set<String> tags, Map<String,String> attr, long id, long typeID){
		GraphElement match = createTranslatedNode(method, XCSG.Variable, tags, attr);	

		match.tag(XCSG.Variable);
		match.tag(XCSG.Identity);
		match.putAttr(XCSG.name, name == null ? UNNAMED_THIS : name);
		
		imported.put(id, match);
		
		addTypeof(imported, match, typeID, StreamAPI.StreamImporter.NOT_DEFINED);
	}
	
	private void createReturn(Map<Long, GraphElement> imported, GraphElement method, String name,
			Set<String> tags, Map<String,String> attr, long id, long typeID, int dimension){
		GraphElement match = createTranslatedNode(method, XCSG.DataFlow_Node, tags, attr);	

		match.tag(XCSG.DataFlow_Node);
		match.tag(XCSG.ReturnValue);
		match.putAttr(XCSG.name, name == null ? UNNAMED_RETURN : name);
		
		imported.put(id, match);
		
		addTypeof(imported, match, typeID, dimension);
		
		ATTR.clear();
		ATTR.put(XCSG.name, XCSG.Returns);
		createEdge(method, match, RETURNS_EDGE_TAGS, ATTR);
	}
	
	@Override
	protected boolean processAnnotation(Map<Long, GraphElement> imported, 
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long extendID,
			Set<Long> implementID) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMatch(parent, XCSG.Java.Annotation, name, null, null);
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(parent, XCSG.Type, tag, attr);
			match.putAttr(XCSG.name, name);
			match.tag(XCSG.Type);
			match.tag(XCSG.Java.Annotation);
		}
		
		extendsAndImplements(imported, match, extendID, implementID);
		imported.put(id, match);
		return true;
	}

	@Override
	protected boolean processClass(Map<Long, GraphElement> imported, 
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long extendID,
			Set<Long> implementID) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMatch(parent, XCSG.Java.Class, name, null, null);
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(parent, XCSG.Type, tag, attr);
			match.putAttr(XCSG.name, name);
			match.tag(XCSG.Type);
			match.tag(XCSG.Java.Class);
		}
		
		if("java.lang".equals(parent.getAttr(XCSG.name))){
			if("Object".equals(name)){
				object = match;
			}
		}
		
		extendsAndImplements(imported, match, extendID, implementID);
		imported.put(id, match);
		return true;
	}

	@Override
	protected boolean processConstructor(Map<Long, GraphElement> imported, 
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, Set<Param> params) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMethod(imported, parent, XCSG.Constructor, name, params);
			AtlasSet<GraphElement> matchDecs = universe().edgesTaggedWithAny(XCSG.Contains).successors(toQ(toGraph(match))).eval().nodes();
			
			for(GraphElement param : matchDecs.taggedWithAny(XCSG.Parameter)){
				Integer idx = (Integer) param.getAttr(XCSG.parameterIndex);
				for(Param p : params){
					if(idx.equals(p.paramIdx)){
						imported.put(p.id, param);
						break;
					}
				}
			}
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(parent, XCSG.Variable, tag, attr);
			match.putAttr(XCSG.name, name);
			match.putAttr("##signature", attr.get(ISUSchema.METHOD_SIGNATURE_STRING));
			match.tag(XCSG.Method);
			match.tag(XCSG.Constructor);
			
			for(Param p : params) createParam(imported, match, p);		
		}
		imported.put(id, match);
		return true;
	}

	@Override
	protected boolean processEnumConstant(Map<Long, GraphElement> imported, 
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long type) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMatch(parent, XCSG.Java.EnumConstant, name, null, null);
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(parent, XCSG.Variable, tag, attr);
			match.putAttr(XCSG.name, name);
			match.tag(XCSG.Variable);
			match.tag(XCSG.Java.EnumConstant);
			addTypeof(imported, match, type, 0);
		}
		imported.put(id, match);
		return true;
	}

	@Override
	protected boolean processEnum(Map<Long, GraphElement> imported, 
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long extendID,
			Set<Long> implementID) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMatch(parent, XCSG.Java.Enum, name, null, null);
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(parent, XCSG.Type, tag, attr);
			match.putAttr(XCSG.name, name);
			match.tag(XCSG.Type);
			match.tag(XCSG.Java.Class);
			match.tag(XCSG.Java.Enum);
		}
		
		extendsAndImplements(imported, match, extendID, implementID);
		imported.put(id, match);
		return true;
	}

	@Override
	protected boolean processField(Map<Long, GraphElement> imported, 
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long type, int dimension) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMatch(parent, XCSG.Field, name, null, null);
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(parent, XCSG.Variable, tag, attr);
			match.putAttr(XCSG.name, name);
			match.tag(XCSG.Variable);
			match.tag(XCSG.Field);
			addTypeof(imported, match, type, dimension);
		}
		imported.put(id, match);
		return true;
	}

	@Override
	protected boolean processInterface(Map<Long, GraphElement> imported, 
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long extendID,
			Set<Long> implementID) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMatch(parent, XCSG.Java.Interface, name, null, null);
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(parent, XCSG.Type, tag, attr);
			match.putAttr(XCSG.name, name);
			match.tag(XCSG.Type);
			match.tag(XCSG.Java.Interface);
		}
		
		if("java.lang".equals(parent.getAttr(XCSG.name))){
			if("Serializable".equals(name)){
				serializable = match;
			}else if("Cloneable".equals(name)){
				cloneable = match;
			}
		}
		
		extendsAndImplements(imported, match, extendID, implementID);
		imported.put(id, match);
		return true;
	}

	@Override
	protected boolean processIOModel(Map<Long, GraphElement> imported, 
			String author, Date created) {
		return false;
	}

	@Override
	protected boolean processLocal(Map<Long, GraphElement> imported, 
			String name, String schemaType, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long type,
			int dimension, int paramIdx) {
		return false;
	}

	@Override
	protected boolean processMethod(Map<Long, GraphElement> imported, 
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, Set<Long> overridesID,
			Set<Param> params) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMethod(imported, parent, XCSG.Method, name, params);
			AtlasSet<GraphElement> matchDecs = universe().edgesTaggedWithAny(XCSG.Contains).successors(toQ(toGraph(match))).eval().nodes();
			
			for(GraphElement param : matchDecs.taggedWithAny(XCSG.Parameter)){
				Integer idx = (Integer) param.getAttr(XCSG.parameterIndex);
				for(Param p : params){
					if(idx.equals(p.paramIdx)){
						imported.put(p.id, param);
						break;
					}
				}
			}
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(parent, XCSG.Method, tag, attr);
			match.putAttr(XCSG.name, name);
			match.putAttr("##signature", attr.get(ISUSchema.METHOD_SIGNATURE_STRING));
			match.tag(XCSG.Method);
			for(Param p : params) createParam(imported, match, p);
		}

		addOverrides(imported, match, overridesID);
		imported.put(id, match);
		return true;
	}

	@Override
	protected boolean processPackage(Map<Long, GraphElement> imported, 
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMatch(parent, XCSG.Package, name, null, null);
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(parent, XCSG.Package, tag, attr);
			match.putAttr(XCSG.name, name);
			match.tag(XCSG.Package);
		}
		imported.put(id, match);
		return true;
	}

	@Override
	protected boolean processPrimitive(Map<Long, GraphElement> imported, 
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr) {
		GraphElement match = null;
		String keyTag = null;
		switch(name){
		case "null":
			keyTag = Attr.Node.NULL_TYPE;
			break;
		case "void":
			keyTag = XCSG.Void;
			break;
		default:
			keyTag = XCSG.Primitive;
		}

		try{
			match = locateExistingMatch(null, keyTag, name, null, null);
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(null, XCSG.Type, tag, attr);
			match.putAttr(XCSG.name, name);
			match.tag(XCSG.Type);
			match.tag(keyTag);
		}
		imported.put(id, match);
		return true;
	}

	@Override
	protected boolean processRelationship(Map<Long, GraphElement> imported, 
			String name, String type, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long originID,
			long destID) {
		return false;
	}
	
	@Override
	protected boolean processThis(Map<Long, GraphElement> imported,
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long type) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMatch(parent, XCSG.Identity, name, null, null);
			imported.put(id, match);
		}catch(NoMatchFoundException e){
			createThis(imported, parent, name, tag, attr, id, type);
		}
		return true;
	}

	@Override
	protected boolean processReturn(Map<Long, GraphElement> imported,
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long type,
			int dimension) {
		GraphElement match = null;
		GraphElement parent = imported.get(parentID);
		try{
			match = locateExistingMatch(parent, XCSG.ReturnValue, name, null, null);
			imported.put(id, match);
		}catch(NoMatchFoundException e){
			createReturn(imported, parent, name, tag, attr, id, type, dimension);
		}
		return true;
	}
	
	private GraphElement locateExistingMethod(Map<Long, GraphElement> imported, GraphElement from, String tag, String name, 
			Set<Param> sParams){
		Graph decG = universe().edgesTaggedWithAny(XCSG.Contains).eval();
		Graph paramG = universe().edgesTaggedWithAny(XCSG.HasParameter).eval();
		Graph typeofG = universe().edgesTaggedWithAny(XCSG.TypeOf).eval();
		
		// Find the types corresponding to parameter types
		GraphElement[] paramTypes = new GraphElement[sParams.size()];
		for(Param p : sParams) {
			paramTypes[p.paramIdx] = findType(imported, p.typeID, p.arrayDimension);
		}
		
		GraphElement match = null;
		
		AtlasSet<GraphElement> decEdges = decG.edges(from, NodeDirection.OUT);
		for(GraphElement e : decEdges){
			GraphElement dest = e.getNode(EdgeDirection.TO);
			if(!dest.taggedWith(tag) || !dest.getAttr(XCSG.name).equals(name)) continue;
			
			AtlasSet<GraphElement> paramEdges = paramG.edges(dest, NodeDirection.OUT);
			if(sParams.size() != paramEdges.size()) continue;
			
			boolean matches = true;
			
			for(GraphElement p : paramEdges){
				GraphElement param = p.getNode(EdgeDirection.TO);
				GraphElement paramType = typeofG.edges(param, NodeDirection.OUT).getFirst().getNode(EdgeDirection.TO);
				Integer paramIdx = (Integer) param.getAttr(XCSG.parameterIndex);
				
				for(Param p2 : sParams){
					if(paramIdx.equals(p2.paramIdx) && paramType != paramTypes[p2.paramIdx]){
						matches = false;
						break;
					}
				}
				if(!matches) break;
			}
			
			if(matches){
				if(match == null){
					match = dest;
				}else{
					String fromName = (String) (from == null ? "null":from.getAttr(XCSG.name));
					String msg = "More than one " + tag + " under " + fromName + " with name " + name + "\n" + match + "\n" + dest;
					throw new MultipleMatchesFoundException(msg);
				}
			}
		}
		
		if(match != null) return match;
		
		StringBuilder sb = new StringBuilder();
		sb.append(name).append("(");
		for(int i=0; i < sParams.size(); i++){
			for(Param p : sParams){
				if(p.paramIdx == i){
					sb.append(imported.get(p.typeID).getAttr(XCSG.name));
					if(i < sParams.size() - 1) sb.append(",");
					break;
				}
			}
		}
		sb.append(")");
		
		throw new NoMatchFoundException("Unable to find method " + sb.toString() + " under " + from.getAttr(XCSG.name));
	}
	
	private GraphElement locateExistingMatch(GraphElement from, String tag, String name, String attrKey, Object attrVal){
		// Build set of candidates in the correct place in the declarative structure
		AtlasSet<GraphElement> decCandidates;
		if(from != null){
			AtlasSet<GraphElement> outEdges = universe().edgesTaggedWithAny(XCSG.Contains).eval().edges(from, NodeDirection.OUT);
			decCandidates = new AtlasHashSet<GraphElement>((int) (outEdges.size() * 1.5));
			for(GraphElement edge : outEdges){
				decCandidates.add(edge.getNode(EdgeDirection.TO));
			}
		} else{
			decCandidates = decRoots;
		}
		
		// Examine candidates for match
		GraphElement match = null;
		for(GraphElement candidate : decCandidates){
			if(candidate.taggedWith(tag) && 
			   (name == null || name.equals(candidate.getAttr(XCSG.name))) && 
			   (attrKey == null || attrVal.equals(candidate.getAttr(attrKey)))){
				if(match == null) match = candidate;
				else{
					String fromName = (String) (from == null ? "null":from.getAttr(XCSG.name));
					String msg = "More than one " + tag + " under " + fromName + " with name " + name + "\n" + match + "\n" + candidate;
					throw new MultipleMatchesFoundException(msg);
				}
			}
		}
		
		if(match == null)
			throw new NoMatchFoundException("Unable to find " + tag + " under " + (from == null ? "null":from.getAttr(XCSG.name)) + " with name " + name);
		
		return match;
	}
	
	private static class NoMatchFoundException extends RuntimeException{
		public NoMatchFoundException(String msg){
			super(msg);
		}
		/**
		 * 
		 */
		private static final long serialVersionUID = -5023829239636767410L;
	}
	
	private static class MultipleMatchesFoundException extends RuntimeException{
		public MultipleMatchesFoundException(String msg){
			super(msg);
		}

		/**
		 * 
		 */
		private static final long serialVersionUID = -4278748695513151803L;

	}

	@Override
	protected boolean processLibrary(Map<Long, GraphElement> imported,
			String name, long id, long parentID, Set<String> tag,
			Map<String, String> attr) {
		GraphElement match = null;
		try{
			match = locateExistingMatch(null, XCSG.Library, name, null, null);
		}catch(NoMatchFoundException e){
			match = createTranslatedNode(null, XCSG.Library, tag, attr);
			match.putAttr(XCSG.name, name);
			match.tag(XCSG.Library);
		}
		imported.put(id, match);
		return true;
	}
}
