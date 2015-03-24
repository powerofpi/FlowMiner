package edu.iastate.flowminer.schema;

import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.util.Map;
import java.util.Set;

import net.ontopia.utils.CompactHashMap;
import net.ontopia.utils.CompactHashSet;

import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

import edu.iastate.flowminer.exception.FlowMinerException;
import edu.iastate.flowminer.exporter.Exporter;
import edu.iastate.flowminer.exporter.ISUExporter;
import edu.iastate.flowminer.importer.ISUImporter;
import edu.iastate.flowminer.importer.Importer;
import edu.iastate.flowminer.miner.ISUMiner;
import edu.iastate.flowminer.miner.Miner;

/**
 * Defines the ISU summary graph schema.
 * @author tdeering
 *
 */
public class ISUSchema extends SummarySchema{
	private ISUSchema(){}
	
	/**
	 * Singleton instance.
	 */
	public static ISUSchema INSTANCE = new ISUSchema();
	
	/**
	 * A tag applied to all inserted (non-index) elements corresponding
	 * to custom ISU summary schema.
	 */
	public static final String ISU = "ISU";
	
	/**
	 * Parameter index of local callsite parameter
	 */
	public static final String PARAM_IDX = "PARAM_IDX";
	
	/**
	 * Attribute key for the most specific type tag
	 */
	public static final String SCHEMA_TYPE = "SCHEMA_TYPE";
	
	/**
	 * Attribute key for the signature string of a method
	 */
	public static final String METHOD_SIGNATURE_STRING = "METHOD_SIGNATURE_STRING";
	
	/**
	 * Parameter index attribute for FLOW_METHOD_DYNAMIC_PARAM edge
	 */
	public static final String DYNAMIC_CALLSITE_PARAM_IDX = "DYNAMIC_CALLSITE_PARAM_IDX";
	
	public interface Node{
		/**
		 * A local definition within a method
		 */
		public static final String LOCAL = "LOCAL";

		/**
		 * Applied to local nodes which are also literals.
		 */
		public static final String LOCAL_LITERAL = "LOCAL_LITERAL";
		
		/**
		 * An alias/name for a call site.
		 * Attribute key mapped to an Address
		 */
		public static final String LOCAL_CALL_SITE = "LOCAL_CALL_SITE";
		
		/**
		 * Local call site which was not conservative and was therefore resolved.
		 */
		public static final String LOCAL_CALL_SITE_RESOLVED = "LOCAL_CALL_SITE_RESOLVED";
		
		/**
		 * Local callsite which was conservative and was therefore unresolved.
		 */
		public static final String LOCAL_CALL_SITE_UNRESOLVED = "LOCAL_CALL_SITE_UNRESOLVED";
		
		/**
		 * Array component
		 */
		public static final String LOCAL_ARRAY_COMPONENT = "LOCAL_ARRAY_COMPONENT";
		
		/**
		 * Array index operator
		 */
		public static final String LOCAL_ARRAY_INDEX_OP = "LOCAL_ARRAY_INDEX_OP";
	}
	
	public interface Edge{
		/**
		 * Edge from an array reference to an array access operator
		 */
		public static final String ARRAY_IDENTITY = "ARRAY_IDENTITY";
		
		/**
		 * Edge from an array index to an array access operator
		 */
		public static final String ARRAY_INDEX = "ARRAY_INDEX";
		
		/**
		 * Edge from an identity to a value read or written to a field
		 */
		public static final String INSTANCE_VARIABLE_ACCESS = "INSTANCE_VARIABLE_ACCESS";

		/**
		 * A summary flow (taint) edge
		 */
		public static final String FLOW = "FLOW";
		
		/**
		 * A flow within a method
		 */
		public static final String FLOW_LOCAL = "FLOW_LOCAL";
		
		/**
		 * A flow within a method to or from an array component.
		 */
		public static final String FLOW_ARRAY = "FLOW_ARRAY";
		
		/**
		 * A flow between a method and field
		 */
		public static final String FLOW_FIELD = "FLOW_FIELD";
		
		/**
		 * A flow involved in a method call
		 */
		public static final String FLOW_METHOD = "FLOW_METHOD";
		
		/**
		 * A flow involved in a static dispatch method call
		 */
		public static final String FLOW_METHOD_RESOLVED = "FLOW_METHOD_RESOLVED";
		
		/**
		 * A flow involved in a dynamic dispatch method call
		 */
		public static final String DYNAMIC_CALLSITE = "DYNAMIC_CALLSITE";
		
		/**
		 * A flow from a local parameter node on the stack to a dynamic dispatch site.
		 */
		public static final String DYNAMIC_CALLSITE_PARAM = "DYNAMIC_CALLSITE_PARAM";

		/**
		 * A flow from a local this node on the stack to a dynamic dispatch site.
		 */
		public static final String DYNAMIC_CALLSITE_THIS = "DYNAMIC_CALLSITE_THIS";
		
		/**
		 * A dynamic dispatch callsite points to its invoked method signature.
		 */
		public static final String DYNAMIC_CALLSITE_SIGNATURE = "DYNAMIC_CALLSITE_SIGNATURE";
		
		/**
		 * A dynamic dispatch callsite points to its invoked type.
		 */
		public static final String DYNAMIC_CALLSITE_TYPE = "DYNAMIC_CALLSITE_TYPE";
	}

	public static final Set<String> EDGE_ARRAY_IDENTITY_TAGS = new CompactHashSet<String>();
	public static final Set<String> EDGE_ARRAY_INDEX_TAGS = new CompactHashSet<String>();
	public static final Set<String> INSTANCE_VARIABLE_ACCESS_TAGS = new CompactHashSet<String>();
	public static final Set<String> EDGE_FLOW_LOCAL_TAGS = new CompactHashSet<String>();
	public static final Set<String> EDGE_FLOW_FIELD_TAGS = new CompactHashSet<String>();
	public static final Set<String> EDGE_FLOW_ARRAY_TAGS = new CompactHashSet<String>();
	public static final Set<String> EDGE_FLOW_METHOD_RESOLVED_TAGS = new CompactHashSet<String>();
	public static final Set<String> EDGE_DYNAMIC_CALLSITE_PARAM_TAGS = new CompactHashSet<String>();
	public static final Set<String> EDGE_DYNAMIC_CALLSITE_THIS_TAGS = new CompactHashSet<String>();
	public static final Set<String> EDGE_DYNAMIC_CALLSITE_SIGNATURE_TAGS = new CompactHashSet<String>();
	public static final Set<String> EDGE_DYNAMIC_CALLSITE_TYPE_TAGS = new CompactHashSet<String>();
	
	public static final Set<String> NODE_LOCAL_TAGS = new CompactHashSet<String>();
	public static final Set<String> NODE_LOCAL_LITERAL_TAGS = new CompactHashSet<String>();
	public static final Set<String> NODE_LOCAL_CALL_SITE_RESOLVED_TAGS = new CompactHashSet<String>();
	public static final Set<String> NODE_LOCAL_CALL_SITE_UNRESOLVED_TAGS = new CompactHashSet<String>();

	public static final Set<String> NODE_LOCAL_ARRAY_COMPONENT_TAGS = new CompactHashSet<String>();
	public static final Set<String> NODE_LOCAL_ARRAY_INDEX_OP_TAGS = new CompactHashSet<String>();
	
	private static final Map<String, String> DISPLAY_NAMES = new CompactHashMap<String,String>();
	static{
		DISPLAY_NAMES.put(Node.LOCAL_ARRAY_COMPONENT, "array");
		DISPLAY_NAMES.put(Node.LOCAL_ARRAY_INDEX_OP, "[]");
		DISPLAY_NAMES.put(Node.LOCAL, "local");
		DISPLAY_NAMES.put(Node.LOCAL_CALL_SITE_RESOLVED, "callsite (resolved)");
		DISPLAY_NAMES.put(Node.LOCAL_CALL_SITE_UNRESOLVED, "callsite (unresolved)");
		DISPLAY_NAMES.put(Edge.ARRAY_IDENTITY, "array ident");
		DISPLAY_NAMES.put(Edge.ARRAY_INDEX, "array idx");
		DISPLAY_NAMES.put(Edge.FLOW_FIELD, "flow (field)");
		DISPLAY_NAMES.put(Edge.FLOW_ARRAY, "flow (array)");
		DISPLAY_NAMES.put(Edge.FLOW_LOCAL, "flow (local)");
		DISPLAY_NAMES.put(Edge.DYNAMIC_CALLSITE_PARAM, "callsite param");
		DISPLAY_NAMES.put(Edge.DYNAMIC_CALLSITE_SIGNATURE, "callsite signature");
		DISPLAY_NAMES.put(Edge.DYNAMIC_CALLSITE_THIS, "callsite this");
		DISPLAY_NAMES.put(Edge.DYNAMIC_CALLSITE_TYPE, "callsite type");
		DISPLAY_NAMES.put(Edge.FLOW_METHOD_RESOLVED, "flow (resolved call)");
		
		NODE_LOCAL_TAGS.add(ISU);
		NODE_LOCAL_TAGS.add(Node.LOCAL);
		
		NODE_LOCAL_LITERAL_TAGS.add(ISU);
		NODE_LOCAL_LITERAL_TAGS.add(Node.LOCAL);
		NODE_LOCAL_LITERAL_TAGS.add(Node.LOCAL_LITERAL);
		
		NODE_LOCAL_CALL_SITE_UNRESOLVED_TAGS.add(ISU);
		NODE_LOCAL_CALL_SITE_UNRESOLVED_TAGS.add(Node.LOCAL);
		NODE_LOCAL_CALL_SITE_UNRESOLVED_TAGS.add(Node.LOCAL_CALL_SITE);
		NODE_LOCAL_CALL_SITE_UNRESOLVED_TAGS.add(Node.LOCAL_CALL_SITE_UNRESOLVED);
		
		NODE_LOCAL_CALL_SITE_RESOLVED_TAGS.add(ISU);
		NODE_LOCAL_CALL_SITE_RESOLVED_TAGS.add(Node.LOCAL);
		NODE_LOCAL_CALL_SITE_RESOLVED_TAGS.add(Node.LOCAL_CALL_SITE);
		NODE_LOCAL_CALL_SITE_RESOLVED_TAGS.add(Node.LOCAL_CALL_SITE_RESOLVED);
		
		NODE_LOCAL_ARRAY_COMPONENT_TAGS.add(ISU);
		NODE_LOCAL_ARRAY_COMPONENT_TAGS.add(Node.LOCAL);
		NODE_LOCAL_ARRAY_COMPONENT_TAGS.add(Node.LOCAL_ARRAY_COMPONENT);
		
		NODE_LOCAL_ARRAY_INDEX_OP_TAGS.add(ISU);
		NODE_LOCAL_ARRAY_INDEX_OP_TAGS.add(Node.LOCAL);
		NODE_LOCAL_ARRAY_INDEX_OP_TAGS.add(Node.LOCAL_ARRAY_INDEX_OP);
		
		EDGE_ARRAY_IDENTITY_TAGS.add(ISU);
		EDGE_ARRAY_IDENTITY_TAGS.add(Edge.ARRAY_IDENTITY);
		
		EDGE_ARRAY_INDEX_TAGS.add(ISU);
		EDGE_ARRAY_INDEX_TAGS.add(Edge.ARRAY_INDEX);
		
		INSTANCE_VARIABLE_ACCESS_TAGS.add(ISU);
		INSTANCE_VARIABLE_ACCESS_TAGS.add(Edge.INSTANCE_VARIABLE_ACCESS);
		
		EDGE_FLOW_LOCAL_TAGS.add(ISU);
		EDGE_FLOW_LOCAL_TAGS.add(Edge.FLOW);
		EDGE_FLOW_LOCAL_TAGS.add(Edge.FLOW_LOCAL);
		
		EDGE_FLOW_FIELD_TAGS.add(ISU);
		EDGE_FLOW_FIELD_TAGS.add(Edge.FLOW);
		EDGE_FLOW_FIELD_TAGS.add(Edge.FLOW_FIELD);
		
		EDGE_FLOW_ARRAY_TAGS.add(ISU);
		EDGE_FLOW_ARRAY_TAGS.add(Edge.FLOW);
		EDGE_FLOW_ARRAY_TAGS.add(Edge.FLOW_ARRAY);
		
		EDGE_FLOW_METHOD_RESOLVED_TAGS.add(ISU);
		EDGE_FLOW_METHOD_RESOLVED_TAGS.add(Edge.FLOW);
		EDGE_FLOW_METHOD_RESOLVED_TAGS.add(Edge.FLOW_METHOD);
		EDGE_FLOW_METHOD_RESOLVED_TAGS.add(Edge.FLOW_METHOD_RESOLVED);
		
		EDGE_DYNAMIC_CALLSITE_PARAM_TAGS.add(ISU);
		EDGE_DYNAMIC_CALLSITE_PARAM_TAGS.add(Edge.DYNAMIC_CALLSITE);
		EDGE_DYNAMIC_CALLSITE_PARAM_TAGS.add(Edge.DYNAMIC_CALLSITE_PARAM);
		
		EDGE_DYNAMIC_CALLSITE_THIS_TAGS.add(ISU);
		EDGE_DYNAMIC_CALLSITE_THIS_TAGS.add(Edge.DYNAMIC_CALLSITE);
		EDGE_DYNAMIC_CALLSITE_THIS_TAGS.add(Edge.DYNAMIC_CALLSITE_THIS);
		
		EDGE_DYNAMIC_CALLSITE_SIGNATURE_TAGS.add(ISU);
		EDGE_DYNAMIC_CALLSITE_SIGNATURE_TAGS.add(Edge.DYNAMIC_CALLSITE);
		EDGE_DYNAMIC_CALLSITE_SIGNATURE_TAGS.add(Edge.DYNAMIC_CALLSITE_SIGNATURE);
		
		EDGE_DYNAMIC_CALLSITE_TYPE_TAGS.add(ISU);
		EDGE_DYNAMIC_CALLSITE_TYPE_TAGS.add(Edge.DYNAMIC_CALLSITE);
		EDGE_DYNAMIC_CALLSITE_TYPE_TAGS.add(Edge.DYNAMIC_CALLSITE_TYPE);
	}

	public static Set<String> getTags(String name){
		switch(name){
		case Node.LOCAL:
			return NODE_LOCAL_TAGS;
		case Node.LOCAL_LITERAL:
			return NODE_LOCAL_LITERAL_TAGS;
		case Node.LOCAL_CALL_SITE_RESOLVED:
			return NODE_LOCAL_CALL_SITE_RESOLVED_TAGS;
		case Node.LOCAL_CALL_SITE_UNRESOLVED:
			return NODE_LOCAL_CALL_SITE_UNRESOLVED_TAGS;
		case Node.LOCAL_ARRAY_COMPONENT:
			return NODE_LOCAL_ARRAY_COMPONENT_TAGS;
		case Node.LOCAL_ARRAY_INDEX_OP:
			return NODE_LOCAL_ARRAY_INDEX_OP_TAGS;
		case Edge.FLOW_FIELD:
			return ISUSchema.EDGE_FLOW_FIELD_TAGS;
		case Edge.FLOW_LOCAL:
			return ISUSchema.EDGE_FLOW_LOCAL_TAGS;
		case Edge.FLOW_ARRAY:
			return ISUSchema.EDGE_FLOW_ARRAY_TAGS;
		case Edge.FLOW_METHOD_RESOLVED:
			return ISUSchema.EDGE_FLOW_METHOD_RESOLVED_TAGS;
		case Edge.DYNAMIC_CALLSITE_PARAM:
			return ISUSchema.EDGE_DYNAMIC_CALLSITE_PARAM_TAGS;
		case Edge.DYNAMIC_CALLSITE_THIS:
			return ISUSchema.EDGE_DYNAMIC_CALLSITE_THIS_TAGS;
		case Edge.DYNAMIC_CALLSITE_SIGNATURE:
			return ISUSchema.EDGE_DYNAMIC_CALLSITE_SIGNATURE_TAGS;
		case Edge.DYNAMIC_CALLSITE_TYPE:
			return ISUSchema.EDGE_DYNAMIC_CALLSITE_TYPE_TAGS;
		case Edge.ARRAY_IDENTITY:
			return ISUSchema.EDGE_ARRAY_IDENTITY_TAGS;
		case Edge.ARRAY_INDEX:
			return ISUSchema.EDGE_ARRAY_INDEX_TAGS;	
		case Edge.INSTANCE_VARIABLE_ACCESS:
			return ISUSchema.INSTANCE_VARIABLE_ACCESS_TAGS;
		default:
			throw new FlowMinerException("Unknown type");
		}
	}
	
	public static String getDisplayName(String tag){
		return DISPLAY_NAMES.get(tag);
	}
	
	/**
	 * Returns the summaries within the given elements, plus how they use the rest of the world.
	 * Excludes how the rest of the world uses them (that's usually huge).
	 * 
	 * @param given
	 * @return
	 */
	public static Q viewSummary(Q given){
		// TODO revisit
		Q decContext = universe().edgesTaggedWithAny(XCSG.Contains);
		Q ffc = universe().edgesTaggedWithAny(ISUSchema.Edge.FLOW_FIELD);
		Q fmrc = universe().edgesTaggedWithAny(ISUSchema.Edge.FLOW_METHOD_RESOLVED);
		Q fmdc = universe().edgesTaggedWithAny(ISUSchema.Edge.DYNAMIC_CALLSITE);
		
		Q declared = decContext.forward(given).retainNodes();
		
		Q result = declared.induce(universe().edgesTaggedWithAny(ISUSchema.ISU)).union(
				ffc.forwardStep(declared), 
				ffc.reverseStep(declared),
				fmrc.reverseStep(declared.nodesTaggedWithAny(ISUSchema.Node.LOCAL_CALL_SITE)),
				fmrc.forwardStep(declared.difference(declared.nodesTaggedWithAny(XCSG.ReturnValue))),
				fmdc.reverseStep(declared),
				fmdc.forwardStep(declared)).retainEdges();
		
		return result.union(decContext.reverse(result));
	}

	@Override
	public Miner getMiner() {
		return new ISUMiner();
	}

	@Override
	public Importer getImporter(boolean existingIndex) {
		return new ISUImporter(existingIndex);
	}

	@Override
	public Exporter getExporter() {
		return new ISUExporter();
	}
}
