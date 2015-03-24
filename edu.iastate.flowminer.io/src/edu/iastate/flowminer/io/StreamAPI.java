package edu.iastate.flowminer.io;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import net.ontopia.utils.CompactHashMap;
import net.ontopia.utils.CompactHashSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

import com.ximpleware.NavException;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.common.IOException;
import edu.iastate.flowminer.io.log.Log;

public class StreamAPI {
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private static CompactHashSet<String> VALID_SCHEMA_STRINGS;
	static{
		Field[] fields = Schema.class.getFields();
		VALID_SCHEMA_STRINGS = new CompactHashSet<String>(fields.length);
		for(Field f : fields){
			try{
				if(String.class.equals(f.getType()))
					VALID_SCHEMA_STRINGS.add((String) f.get(null));
			} catch(IllegalArgumentException | IllegalAccessException e){}
		}
	}
	
	public static interface StreamImporter{
		public static final String ATTR_SEPARATOR = ";";
		public static final int NOT_DEFINED = -1;
		
		public void processLibrary(String name, long id, long parentID, Set<String> tag, Map<String,String> attr);
		public void processAnnotation(String name, long id, long parentID, Set<String> tag, Map<String,String> attr, long extendID, Set<Long> implementID);
		public void processClass(String name, long id, long parentID, Set<String> tag, Map<String,String> attr, long extendID, Set<Long> implementID);
		public void processConstructor(String name, long id, long parentID, Set<String> tag, Map<String,String> attr, Set<Param> params);
		public void processEnumConstant(String name, long id, long parentID, Set<String> tag, Map<String,String> attr, long type);
		public void processEnum(String name, long id, long parentID, Set<String> tag, Map<String,String> attr, long extendID, Set<Long> implementID);
		public void processField(String name, long id, long parentID, Set<String> tag, Map<String,String> attr, long type, int dimension);
		public void processInterface(String name, long id, long parentID, Set<String> tag, Map<String,String> attr, long extendID, Set<Long> implementID);
		public void processIOModel(String author, Date created);
		public void processLocal(String name, String schemaType, long id, long parentID, Set<String> tag, Map<String,String> attr, long type, int dimension, int paramIdx);
		public void processMethod(String name, long id, long parentID, Set<String> tag, Map<String,String> attr, Set<Long> overridesID, Set<Param> params);
		public void processPackage(String name, long id, long parentID, Set<String> tag, Map<String,String> attr);
		public void processPrimitive(String name, long id, long parentID, Set<String> tag, Map<String,String> attr);
		public void processRelationship(String name, String type, long id, long parentID, Set<String> tag, Map<String,String> attr, long originID, long destID);
		public void processThis(String name, long id, long parentID, Set<String> tag, Map<String,String> attr, long type);
		public void processReturn(String name, long id, long parentID, Set<String> tag, Map<String,String> attr, long type, int dimension);

		public static class Param {
			public int paramIdx;
			public String name;
			public long id = NOT_DEFINED;
			public boolean surface;
			public long parentID; 
			public Set<String> tag;
			public Map<String,String> attr;
			public long typeID;
			public int arrayDimension;
		}
	}
	
	public static void importFromFile(final IProgressMonitor mon, final String source, final StreamImporter importer) throws Throwable {
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
					
					StreamAPI s = new StreamAPI(mon, vn, importer);
					s.streamImport();
					
					// Re-enable if you need to debug problems with not all callbacks executing.
					//errorDetection(s);
					
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
			Job job = new Job("Using Stream API to Import File"){
				@Override
				protected IStatus run(final IProgressMonitor monitor) {
					return new JobImporter(monitor).doImport();
				}
			};
			job.setPriority(Job.LONG);
			job.schedule();
			job.join();
		} else{
			if(mon.isCanceled()) return;
			try{
				new JobImporter(mon).doImport();
			}finally{
				mon.done();
			}
		}
		
		if(error[0] != null) throw error[0];
	}
	
	@SuppressWarnings("unused")
	private static void errorDetection(StreamAPI s){
		PrecedenceCallbackRunner pcr = s.pcr;
		
		// Detect cycles
		Set<Long> stack = new CompactHashSet<Long>();
		for(Long uncompleted : pcr.queued.keySet()){
			for(StreamImporterCallback sic : pcr.queued.get(uncompleted)){
				cycleDetect(pcr, stack, sic);
			}
		}
		
		// Detect quantity mismatch
		long added = s.pcr.numAdded;
		long completed = s.pcr.numCompleted;
		if(added != completed) {
			Long someKey = pcr.queued.keySet().iterator().next();
			Set<StreamImporterCallback> uncompletedExamples = (Set<StreamImporterCallback>) s.pcr.queued.get(someKey);
			StreamImporterCallback uncompletedExample = uncompletedExamples.iterator().next();
			
			throw new IOException("Parsed " + added + " stream callbacks but " + completed + " were run! Example:\n" + uncompletedExample);
		}
	}
	
	private static void cycleDetect(PrecedenceCallbackRunner pcr, Set<Long> stack, StreamImporterCallback current){
		Long id = current.id;
		if(stack.contains(id)){
			StringBuilder sb = new StringBuilder();
			
			for(Long l : stack){
				sb.append("\n").append(pcr.added.get(l));
			}
			
			throw new IOException("Detected cycle!" + sb.toString());
		}
		
		
		
		stack.add(id);
		for(Long l : current.prereqs) cycleDetect(pcr, stack, pcr.added.get(l));
		stack.remove(id);
	}
	
	private static void throwForMissingElement(String tag){
		throw new IOException("Required element missing: " + tag + " (" + fieldForElement(tag) + ")");
	}
	
	private static String fieldForElement(String tag){
		Field[] fields = Schema.class.getFields();
		
		for(Field f : fields){
			if(String.class.equals(f.getType())){
				String s = null;
				try{
					s = (String) f.get(null);
				} catch(IllegalArgumentException | IllegalAccessException e){}
				if(tag.equals(s)){
					return f.getName();
				}
			}
		}
		
		return null;
	}
	
	IProgressMonitor mon;
	VTDNav vn;
	StreamImporter importer;
	PrecedenceCallbackRunner pcr = new PrecedenceCallbackRunner();
	
	private StreamAPI(IProgressMonitor mon, VTDNav vn, StreamImporter importer){
		this.mon = mon;
		this.vn = vn;
		this.importer = importer;
	}
	
	private void streamImport() throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, ParseException, NoSuchMethodException{
		new IOModelCallback();
	}
	
	private abstract class StreamImporterCallback{
		Set<Long> prereqs = new CompactHashSet<Long>();
		long parent = (long) StreamImporter.NOT_DEFINED;
		long id = (long) StreamImporter.NOT_DEFINED;
		
		public StreamImporterCallback(long parent) throws NavException{
			this.parent = parent;
			if(parent > StreamImporter.NOT_DEFINED) prereqs.add(parent);
			vn.toElement(VTDNav.FC);
		}
		
		final void callback(){
			pcr.callback(this);
		}
		
		abstract void doCallback();
	}
	
	private class IOModelCallback extends StreamImporterCallback{
		String author;
		Date date; 
		
		public IOModelCallback() throws NavException, ParseException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException {
			super(-1);
			if(mon.isCanceled()) return;
			id = (long) -1;
			author = parseString(Schema.IOMODEL_AUTHOR, false);
			if(vn.toElement(VTDNav.NS))
				date = parseDate(Schema.IOMODEL_CREATED, false);
			long numElements = -1;
			if(vn.toElement(VTDNav.NS)){
				numElements = parseLong(Schema.IOMODEL_NUM_ELEMENTS, false); 
				mon = SubMonitor.convert(mon, (int) numElements);
			}
			this.callback();
			
			try{
				if(vn.toElement(VTDNav.NS))
					parseChildren((long) -1, Schema.IOMODEL_PRIMITIVE, PrimitiveCallback.class);
				if(vn.toElement(VTDNav.NS))
					parseChildren((long) -1, Schema.IOMODEL_LIBRARY, LibraryCallback.class);
				if(vn.toElement(VTDNav.NS))
					parseChildren((long) -1, Schema.IOMODEL_RELATIONSHIP, RelationshipCallback.class);
				vn.toElement(VTDNav.P);
			}finally{
				if(numElements > -1) mon.done();
			}
		}

		@Override
		void doCallback() {
			importer.processIOModel(author, date);
			mon.worked(1);
		}
	}
	
	private class LibraryCallback extends ElementCallback{
		public LibraryCallback(long parent) throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException {
			super(parent);
			if(mon.isCanceled()) return;
			this.callback();
			
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.LIBRARY_PACKAGE, PackageCallback.class);
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processLibrary(name, id, parent, tags, attr);
			mon.worked(1);
		}
	}
	
	private abstract class ElementCallback extends StreamImporterCallback{
		String name;
		Set<String> tags = new CompactHashSet<String>();
		Map<String, String> attr = new CompactHashMap<String,String>();
		
		public ElementCallback(long parent) throws NavException {
			super(parent);
			if(mon.isCanceled()) return;
			name = parseString(Schema.ELEMENT_NAME, false);
			if(name != null) vn.toElement(VTDNav.NS);
			id = parseLong(Schema.ELEMENT_ID, true);
			if(vn.toElement(VTDNav.NS)) 
				parseTags(Schema.ELEMENT_TAG, tags);
			if(vn.toElement(VTDNav.NS)) 
				parseAttr(Schema.ELEMENT_ATTR, attr);
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder();
			sb.append("{");
			for(Long l : prereqs) sb.append(Long.toString(l, Schema.RADIX)).append(",");
			sb.deleteCharAt(sb.length()-1).append("}");
			
			return "NAME: " + name +
				"\nTYPE: " + this.getClass().getSimpleName() +
				"\nID: " + Long.toString(id, Schema.RADIX) +
				"\nDDEPENDENCIES: " + sb.toString();
		}
	}
	
	private class PackageCallback extends ElementCallback{
		public PackageCallback(long parent) throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException {
			super(parent);
			if(mon.isCanceled()) return;
			this.callback();
			
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.PACKAGE_CLASS, ClassCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.PACKAGE_INTERFACE, InterfaceCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.PACKAGE_ENUM, EnumCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.PACKAGE_ANNOTATION, AnnotationCallback.class);
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processPackage(name, id, parent, tags, attr);
			mon.worked(1);
		}
	}
	
	private class RelationshipCallback extends ElementCallback{
		long originID = StreamImporter.NOT_DEFINED;
		long destID = StreamImporter.NOT_DEFINED;
		String type;
		
		public RelationshipCallback(long parent) throws NavException {
			super(parent);
			if(mon.isCanceled()) return;
			vn.toElement(VTDNav.NS);
			originID = parseLong(Schema.RELATIONSHIP_ORIGIN_ID, true);
			vn.toElement(VTDNav.NS);
			destID = parseLong(Schema.RELATIONSHIP_DEST_ID, true);
			vn.toElement(VTDNav.NS);
			type = parseString(Schema.RELATIONSHIP_SCHEMA_TYPE, true);
			prereqs.add(originID);
			prereqs.add(destID);
			
			this.callback();
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processRelationship(name, type, id, parent, tags, attr, originID, destID);
			mon.worked(1);
		}
	}
	
	private class PrimitiveCallback extends ElementCallback{
		public PrimitiveCallback(long parent) throws NavException {
			super(parent);
			if(mon.isCanceled()) return;
			this.callback();
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processPrimitive(name, id, parent, tags, attr);
			mon.worked(1);
		}
	}
	
	private static void transferParamContents(ParamCallback from, StreamImporter.Param to){
		to.id = from.id;
		to.parentID = from.parent;
		to.name = from.name;
		to.attr = from.attr;
		to.tag = from.tags;
		to.arrayDimension = from.arrayDim;
		to.typeID = from.typeID;
		to.paramIdx = from.paramIdx;
	}
	
	private class MethodCallback extends ElementCallback{
		Set<Long> overridesID = new CompactHashSet<Long>();
		Set<StreamImporter.Param> mSigParam = new CompactHashSet<StreamImporter.Param>();
		
		public MethodCallback(long parent) throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException {
			super(parent);
			if(mon.isCanceled()) return;
			
			if(vn.toElement(VTDNav.NS)){
				for(StreamImporterCallback sic : parseChildren(id, Schema.METHOD_PARAM, ParamCallback.class)){
					ParamCallback pc = (ParamCallback) sic;
					prereqs.add(pc.typeID);
					
					StreamImporter.Param p = new StreamImporter.Param();
					transferParamContents(pc, p);
					mSigParam.add(p);
				}
			}

			if(vn.toElement(VTDNav.NS)) parseLongs(Schema.METHOD_OVERRIDES, overridesID);	
			for(Long l : overridesID) prereqs.add(l);
			
			this.callback();
			
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.METHOD_RETURNED, ReturnCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.METHOD_CONTEXT_THIS, ThisCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.METHOD_LOCAL_CLASS, ClassCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.METHOD_LOCAL_INTERFACE, InterfaceCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.METHOD_LOCAL_ENUM, EnumCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.METHOD_LOCAL_ANNOTATION, AnnotationCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.METHOD_LOCAL_VAR, LocalCallback.class);
			
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processMethod(name, id, parent, tags, attr, overridesID, mSigParam);
			mon.worked(1 + mSigParam.size());
		}
	}
	
	private class ConstructorCallback extends MethodCallback{
		public ConstructorCallback(long parent) throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException {
			super(parent);
		}
		
		@Override
		void doCallback() {
			importer.processConstructor(name, id, parent, tags, attr, mSigParam);
			mon.worked(1 + mSigParam.size());
		}
	}
	
	private abstract class VarCallback extends ElementCallback{
		long typeID = StreamImporter.NOT_DEFINED;
		int arrayDim = StreamImporter.NOT_DEFINED;
		
		public VarCallback(long parent) throws NavException {
			super(parent);
			if(mon.isCanceled()) return;
			vn.toElement(VTDNav.NS);
			typeID = parseLong(Schema.VAR_TYPE, true);
			prereqs.add(typeID);
			if(vn.toElement(VTDNav.NS))
				arrayDim = parseInt(Schema.VAR_ARRAY_DIM, false);
		}
	}
	
	private abstract class NonPrimitiveTypeCallback extends ElementCallback{
		long extendsID = StreamImporter.NOT_DEFINED;
		Set<Long> implementsIDs = new CompactHashSet<Long>();
		
		public NonPrimitiveTypeCallback(long parent) throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException {
			super(parent);
			if(mon.isCanceled()) return;
			if(vn.toElement(VTDNav.NS))
				extendsID = parseLong(Schema.TYPE_NON_PRIMITIVE_EXTENDS, false);
			if(vn.toElement(VTDNav.NS))
				parseLongs(Schema.TYPE_NON_PRIMITIVE_IMPLEMENTS, implementsIDs);
			if(extendsID > StreamImporter.NOT_DEFINED) prereqs.add(extendsID);
			for(Long l : implementsIDs) prereqs.add(l);
			this.callback();
			
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.TYPE_NON_PRIMITIVE_CONSTRUCTOR, ConstructorCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.TYPE_NON_PRIMITIVE_METHOD, MethodCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.TYPE_NON_PRIMITIVE_FIELD, FieldCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.TYPE_NON_PRIMITIVE_NESTED_CLASS, ClassCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.TYPE_NON_PRIMITIVE_NESTED_INTERFACE, InterfaceCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.TYPE_NON_PRIMITIVE_NESTED_ENUM, EnumCallback.class);
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.TYPE_NON_PRIMITIVE_NESTED_ANNOTATION, AnnotationCallback.class);
		}
	}
	
	private class AnnotationCallback extends NonPrimitiveTypeCallback{
		public AnnotationCallback(long parent) throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException {
			super(parent);
			if(mon.isCanceled()) return;
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processAnnotation(name, id, parent, tags, attr, extendsID, implementsIDs);
			mon.worked(1);
		}
	}
	
	private class ClassCallback extends NonPrimitiveTypeCallback{
		public ClassCallback(long parent) throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException {
			super(parent);
			if(mon.isCanceled()) return;
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processClass(name, id, parent, tags, attr, extendsID, implementsIDs);
			mon.worked(1);
		}
	}
	
	private class EnumCallback extends NonPrimitiveTypeCallback{
		public EnumCallback(long parent) throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException {
			super(parent);
			if(mon.isCanceled()) return;
			if(vn.toElement(VTDNav.NS))
				parseChildren(id, Schema.ENUM_TYPE_NON_PRIMITIVE_CONSTANT, EnumConstantCallback.class);
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processEnum(name, id, parent, tags, attr, extendsID, implementsIDs);
			mon.worked(1);
		}
	}
	
	private class InterfaceCallback extends NonPrimitiveTypeCallback{
		public InterfaceCallback(long parent) throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException {
			super(parent);
			if(mon.isCanceled()) return;
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processInterface(name, id, parent, tags, attr, extendsID, implementsIDs);
			mon.worked(1);
		}
	}
	
	private class EnumConstantCallback extends VarCallback{
		public EnumConstantCallback(long parent) throws NavException {
			super(parent);
			if(mon.isCanceled()) return;
			this.callback();
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processEnumConstant(name, id, parent, tags, attr, typeID);
			mon.worked(1);
		}
	}
	
	private class LocalCallback extends VarCallback{
		private String type;
		private int paramIdx = -1;
		
		public LocalCallback(long parent) throws NavException {
			super(parent);
			if(mon.isCanceled()) return;
			vn.toElement(VTDNav.NS);
			type = parseString(Schema.LOCAL_VAR_SCHEMA_TYPE, true);
			vn.toElement(VTDNav.NS);
			paramIdx = parseInt(Schema.PARAM_VAR_INDEX, false);
			this.callback();
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processLocal(name, type, id, parent, tags, attr, typeID, arrayDim, paramIdx);
			mon.worked(1);
		}
	}
	
	private class FieldCallback extends VarCallback{
		public FieldCallback(long parent) throws NavException {
			super(parent);
			if(mon.isCanceled()) return;
			this.callback();
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processField(name, id, parent, tags, attr, typeID, arrayDim);
			mon.worked(1);
		}
	}
	
	private class ParamCallback extends VarCallback{
		int paramIdx = StreamImporter.NOT_DEFINED;
		
		public ParamCallback(long parent) throws NavException {
			super(parent);
			if(mon.isCanceled()) return;
			vn.toElement(VTDNav.NS);
			paramIdx = parseInt(Schema.PARAM_VAR_INDEX, true);
			this.callback();
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			mon.worked(1);
		}
	}
	
	private class ReturnCallback extends VarCallback{
		public ReturnCallback(long parent) throws NavException {
			super(parent);
			if(mon.isCanceled()) return;
			this.callback();
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processReturn(name, id, parent, tags, attr, typeID, arrayDim);
			mon.worked(1);
		}
	}
	
	private class ThisCallback extends VarCallback{
		public ThisCallback(long parent) throws NavException {
			super(parent);
			if(mon.isCanceled()) return;
			this.callback();
			vn.toElement(VTDNav.P);
		}

		@Override
		void doCallback() {
			importer.processThis(name, id, parent, tags, attr, typeID);
			mon.worked(1);
		}
	}
	
	private Date parseDate(String tag, boolean required) throws NavException, ParseException{
		String element = vn.toString(vn.getCurrentIndex());
		if(tag.equals(element)){
			return sdf.parse(parseString(tag, required));
		}else{
			if(required) throwForMissingElement(tag);
			vn.toElement(VTDNav.PS);
		}
		return null;
	}
	
	private String parseString(String tag, boolean required) throws NavException{
		String element = vn.toString(vn.getCurrentIndex());
		if(tag.equals(element)){
			return vn.toString(vn.getText());
		}else{
			if(required) throwForMissingElement(tag);
			vn.toElement(VTDNav.PS);
		}
		return null;
	}
	
	private long parseLong(String tag, boolean required) throws NavException{
		String element = vn.toString(vn.getCurrentIndex());
		if(tag.equals(element)){
			return Long.parseLong(vn.toString(vn.getText()), Schema.RADIX);
		}else{
			if(required) throwForMissingElement(tag);
			vn.toElement(VTDNav.PS);
		}
		return StreamImporter.NOT_DEFINED;
	}
	
	private int parseInt(String tag, boolean required) throws NavException{
		String element = vn.toString(vn.getCurrentIndex());
		if(tag.equals(element)){
			return Integer.parseInt(vn.toString(vn.getText()), Schema.RADIX);
		}else{
			if(required) throwForMissingElement(tag);
			vn.toElement(VTDNav.PS);
		}
		return (int) StreamImporter.NOT_DEFINED;
	}

	private void parseTags(String elementTag, Set<String> tags) throws NavException{
		do{
			String element = vn.toString(vn.getCurrentIndex());
			if(elementTag.equals(element)){
				tags.add(parseString(Schema.ELEMENT_TAG, true));
			}else{
				vn.toElement(VTDNav.PS);
				return;
			}
		}while(vn.toElement(VTDNav.NS));
	}

	private void parseAttr(String tag,  Map<String,String> attr) throws NavException{
		do{
			String element = vn.toString(vn.getCurrentIndex());
			if(tag.equals(element)){
				vn.toElement(VTDNav.FC);
				String key = parseString(Schema.ATTR_KEY, true);
				vn.toElement(VTDNav.NS);
				String val = parseString(Schema.ATTR_VAL, true);
				vn.toElement(VTDNav.P);
				attr.put(key, val);
			}else{
				vn.toElement(VTDNav.PS);
				return;
			}
		}while(vn.toElement(VTDNav.NS));
	}
	
	private void parseLongs(String tag, Set<Long> longs) throws NavException{
		do{
			String element = vn.toString(vn.getCurrentIndex());
			if(tag.equals(element)){
				longs.add(parseLong(tag, false));
			}else{
				vn.toElement(VTDNav.PS);
				return;
			}
		}while(vn.toElement(VTDNav.NS));
	}
	
	private Set<StreamImporterCallback> parseChildren(long parentID, String tag, Class<? extends StreamImporterCallback> clazz) throws NavException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException{
		Set<StreamImporterCallback> ret = new CompactHashSet<StreamImporterCallback>();
		do{
			String element = vn.toString(vn.getCurrentIndex());
			if(tag.equals(element)){
				ret.add((StreamImporterCallback) clazz.getDeclaredConstructors()[0].newInstance(StreamAPI.this, parentID));
			}else{
				vn.toElement(VTDNav.PS);
				break;
			}
		}while(vn.toElement(VTDNav.NS));
		return ret;
	}
	
	private class PrecedenceCallbackRunner {
		Set<Long> completed;
		Map<Long, Set<StreamImporterCallback>> queued;
		Map<Long, StreamImporterCallback> added;
		long numAdded, numCompleted;
		
		public PrecedenceCallbackRunner(){
			completed = new CompactHashSet<Long>();
			queued = new CompactHashMap<Long, Set<StreamImporterCallback>>();
			added = new CompactHashMap<Long, StreamImporterCallback>();
		}
		
		/**
		 * Add the callback, executing if it's ready. Also executes subsequent dependencies which
		 * are ready after this one is executed.
		 * 
		 * @param callback
		 */
		public void callback(StreamImporterCallback callback){ 
			numAdded++;
			added.put(callback.id, callback);
			if(mon.isCanceled()) return;
			callback.prereqs.removeAll(completed);
			
			if(callback.prereqs.isEmpty()){
				completeCallback(callback);
			}else{
				for(Long l : callback.prereqs) addDependency(l, callback);
			}
		}
		
		private void addDependency(Long l, StreamImporterCallback callback){
			Set<StreamImporterCallback> dependencySet = queued.get(l);
			if(dependencySet == null){
				dependencySet = new CompactHashSet<StreamImporterCallback>();
				queued.put(l, dependencySet);
			}
			dependencySet.add(callback);
		}
		
		private void completeCallback(StreamImporterCallback callback){
			numCompleted++;
			if(mon.isCanceled()) return;
			callback.doCallback();
			Long id = callback.id;
			completed.add(id);
			added.remove(id);
			
			Set<StreamImporterCallback> dependencySet = queued.remove(id);
			if(dependencySet == null) return;
			
			for(StreamImporterCallback dependency : dependencySet){
				if(dependency.prereqs.remove(id) && dependency.prereqs.isEmpty()){
					completeCallback(dependency);
				}
			}
		}
	}
}
