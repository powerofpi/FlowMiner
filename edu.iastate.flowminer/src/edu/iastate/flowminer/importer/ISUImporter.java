package edu.iastate.flowminer.importer;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

import edu.iastate.flowminer.io.StreamAPI.StreamImporter.Param;
import edu.iastate.flowminer.schema.ISUSchema;

public class ISUImporter extends Importer{	
	public ISUImporter(boolean existingIndex) {
		super(existingIndex);
	}
	
	@Override
	protected boolean processLocal(Map<Long, GraphElement> imported,
			String name, String schemaType, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long type, int dimension, int paramIdx) {
		GraphElement match = createTranslatedNode(imported.get(parentID), XCSG.LocalDataFlow, tag, attr);
		imported.put(id, match);
		addTypeof(imported, match, type, dimension);
		
		for(String s : ISUSchema.getTags(schemaType)) match.tag(s);
				
		if(paramIdx > -1){
			match.putAttr(ISUSchema.PARAM_IDX, paramIdx);
		}
		
		switch(schemaType){
		case ISUSchema.Node.LOCAL_LITERAL:
			match.putAttr(XCSG.name, name);
			break;
		default:
			match.putAttr(XCSG.name, ISUSchema.getDisplayName(schemaType));
		}

		return true;
	}
	@Override
	protected boolean processRelationship(Map<Long, GraphElement> imported,
			String name, String schemaType, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long originID, long destID) {
		GraphElement origin = imported.get(originID);
		GraphElement dest = imported.get(destID);
		GraphElement match = createBlankEdge(origin, dest);
		
		for(String s : ISUSchema.getTags(schemaType)) match.tag(s);
		match.putAttr(XCSG.name, ISUSchema.getDisplayName(schemaType));

		return true;
	}
	@Override
	protected boolean processAnnotation(Map<Long, GraphElement> imported,
			String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long extendID, Set<Long> implementID) {
		return false;
	}
	@Override
	protected boolean processClass(Map<Long, GraphElement> imported,
			String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long extendID, Set<Long> implementID) {
		return false;
	}
	@Override
	protected boolean processConstructor(Map<Long, GraphElement> imported,
			String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			Set<Param> params) {
		return false;
	}
	@Override
	protected boolean processEnumConstant(Map<Long, GraphElement> imported,
			String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr, long type) {
		return false;
	}
	@Override
	protected boolean processEnum(Map<Long, GraphElement> imported,
			String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long extendID, Set<Long> implementID) {
		return false;
	}
	@Override
	protected boolean processField(Map<Long, GraphElement> imported,
			String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long type, int dimension) {
		return false;
	}
	@Override
	protected boolean processInterface(Map<Long, GraphElement> imported,
			String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			long extendID, Set<Long> implementID) {
		return false;
	}
	@Override
	protected boolean processIOModel(Map<Long, GraphElement> imported,
			String author, Date created) {
		return false;
	}

	@Override
	protected boolean processMethod(Map<Long, GraphElement> imported,
			String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr,
			Set<Long> overridesID, Set<Param> params) {
		return false;
	}
	@Override
	protected boolean processPackage(Map<Long, GraphElement> imported,
			String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr) {
		return false;
	}
	@Override
	protected boolean processPrimitive(Map<Long, GraphElement> imported,
			String name, long id,
			long parentID, Set<String> tag, Map<String, String> attr) {
		return false;
	}

	@Override
	protected boolean processThis(Map<Long, GraphElement> imported,
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long type) {
		return false;
	}

	@Override
	protected boolean processReturn(Map<Long, GraphElement> imported,
			String name, long id, long parentID,
			Set<String> tag, Map<String, String> attr, long type,
			int dimension) {
		return false;
	}
	
	@Override
	protected boolean processLibrary(Map<Long, GraphElement> imported,
			String name, long id, long parentID, Set<String> tag,
			Map<String, String> attr) {
		return false;
	}
}