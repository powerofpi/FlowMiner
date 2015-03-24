package edu.iastate.flowminer.io.model;

import java.text.ParseException;
import java.util.Set;

import net.ontopia.utils.CompactHashSet;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public class MethodElement extends Element {
	private Set<ParamVarElement> param = new CompactHashSet<ParamVarElement>();
	private ReturnVarElement returned;
	private ThisVarElement context_this;
	private Set<ClassTypeElement> local_class = new CompactHashSet<ClassTypeElement>();
	private Set<AnnotationTypeElement> local_annotation = new CompactHashSet<AnnotationTypeElement>();
	private Set<EnumTypeElement> local_enum = new CompactHashSet<EnumTypeElement>();
	private Set<InterfaceTypeElement> local_interface = new CompactHashSet<InterfaceTypeElement>();
	private Set<LocalVarElement> local_var = new CompactHashSet<LocalVarElement>();
	private Set<Long> overrides = new CompactHashSet<Long>();
	
	public MethodElement(IProgressMonitor mon, VTDNav vn) throws NumberFormatException, NavException, ParseException {
		super(mon, vn);
		if(!(this instanceof ConstructorElement)) parse();
	}
	
	public MethodElement(long id, String name) {
		super(id, name);
	}

	public MethodElement(String name) {
		super(name);
	}
	
	@Override
	public void releaseMemory(){
		super.releaseMemory();
		returned = null;
		context_this = null;
		param = null;
		local_class = null;
		local_annotation = null;
		local_enum = null;
		local_interface = null;
		local_var = null;
	}

	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		if(super.interpretChild(mon, vn, name)) return true;
		switch (name) {
		case Schema.METHOD_PARAM:
			param.add(new ParamVarElement(mon, vn));
			return true;
		case Schema.METHOD_LOCAL_CLASS:
			local_class.add(new ClassTypeElement(mon, vn));
			return true;
		case Schema.METHOD_LOCAL_INTERFACE:
			local_interface.add(new InterfaceTypeElement(mon, vn));
			return true;
		case Schema.METHOD_LOCAL_ENUM:
			local_enum.add(new EnumTypeElement(mon, vn));
			return true;
		case Schema.METHOD_LOCAL_ANNOTATION:
			local_annotation.add(new AnnotationTypeElement(mon, vn));
			return true;
		case Schema.METHOD_RETURNED:
			returned = new ReturnVarElement(mon, vn);
			return true;
		case Schema.METHOD_CONTEXT_THIS:
			context_this = new ThisVarElement(mon, vn);
			return true;
		case Schema.METHOD_LOCAL_VAR:
			local_var.add(new LocalVarElement(mon, vn));
			return true;
		case Schema.METHOD_OVERRIDES:
			overrides.add(Long.parseLong(vn.toString(vn.getText()),Schema.RADIX));
			return true;
		default:
			return false;
		}
	}
	
	public Set<ParamVarElement> getParam() {
		return param;
	}

	public ReturnVarElement getReturned() {
		return returned;
	}

	public void setReturned(ReturnVarElement returned) {
		this.returned = returned;
	}
	
	public Set<Long> getOverrides(){
		return overrides;
	}

	public Set<ClassTypeElement> getLocal_class() {
		return local_class;
	}

	public Set<AnnotationTypeElement> getLocal_annotation() {
		return local_annotation;
	}

	public Set<EnumTypeElement> getLocal_enum() {
		return local_enum;
	}

	public Set<InterfaceTypeElement> getLocal_interface() {
		return local_interface;
	}

	public Set<LocalVarElement> getLocalVar(){
		return local_var;
	}
	
	public ThisVarElement getContextThis(){
		return context_this;
	}
	
	public void setContextThis(ThisVarElement toSet){
		context_this = toSet;
	}
	
	@Override
	public void doConvert(StringBuilder sb, String name){
		for (ParamVarElement e : param) {
			e.convert(sb, Schema.METHOD_PARAM);
		}
		
		for(Long l : overrides){
			createNode(sb, Schema.METHOD_OVERRIDES, Long.toString(l, Schema.RADIX));
		}
		
		if (returned != null) {
			returned.convert(sb, Schema.METHOD_RETURNED);
		}
		
		if(context_this != null){
			context_this.convert(sb, Schema.METHOD_CONTEXT_THIS);
		}

		for (ClassTypeElement e : local_class) {
			e.convert(sb, Schema.METHOD_LOCAL_CLASS);
		}
		
		for (InterfaceTypeElement e : local_interface) {
			e.convert(sb, Schema.METHOD_LOCAL_INTERFACE);
		}
		
		for (EnumTypeElement e : local_enum) {
			e.convert(sb, Schema.METHOD_LOCAL_ENUM);
		}
		
		for (AnnotationTypeElement e : local_annotation) {
			e.convert(sb, Schema.METHOD_LOCAL_ANNOTATION);
		}
		
		for(LocalVarElement e : local_var){
			e.convert(sb, Schema.METHOD_LOCAL_VAR);
		}
	}

	@Override
	public int subtreeSize() {
		int size = 1;
		for(XMLConvertable dc : param) size += dc.subtreeSize();
		for(XMLConvertable dc : local_class) size += dc.subtreeSize();
		for(XMLConvertable dc : local_interface) size += dc.subtreeSize();
		for(XMLConvertable dc : local_enum) size += dc.subtreeSize();
		for(XMLConvertable dc : local_annotation) size += dc.subtreeSize();
		for(XMLConvertable dc : local_var) size += dc.subtreeSize();
		if(returned != null) size += 1;
		if(context_this != null) size += 1;
		return size;
	}
}
