package edu.iastate.flowminer.io.model;

import java.text.ParseException;
import java.util.Set;

import net.ontopia.utils.CompactHashSet;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public abstract class NonPrimitiveTypeElement extends TypeElement {
	private Set<FieldVarElement> field = new CompactHashSet<FieldVarElement>();
	private Set<MethodElement> method = new CompactHashSet<MethodElement>();
	private Set<ClassTypeElement> nestedClass = new CompactHashSet<ClassTypeElement>();
	private Set<InterfaceTypeElement> nestedInterface = new CompactHashSet<InterfaceTypeElement>();
	private Set<EnumTypeElement> nestedEnum = new CompactHashSet<EnumTypeElement>();
	private Set<AnnotationTypeElement> nestedAnnotation = new CompactHashSet<AnnotationTypeElement>();
	
	private Set<ConstructorElement> constructor = new CompactHashSet<ConstructorElement>();
	private long extend = NOT_DEFINED;
	private Set<Long> implement = new CompactHashSet<Long>();
	
	public NonPrimitiveTypeElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
	}
	
	@Override
	public void releaseMemory(){
		super.releaseMemory();
		field = null;
		method = null;
		nestedClass = null;
		nestedInterface = null;
		nestedEnum = null;
		nestedAnnotation = null;
		constructor = null;
		implement = null;
	}
	
	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		if(super.interpretChild(mon, vn, name)) return true;
		switch (name) {
		case Schema.TYPE_NON_PRIMITIVE_FIELD:
			field.add(new FieldVarElement(mon, vn));
			return true;
		case Schema.TYPE_NON_PRIMITIVE_METHOD:
			method.add(new MethodElement(mon, vn));
			return true;
		case Schema.TYPE_NON_PRIMITIVE_NESTED_CLASS:
			nestedClass.add(new ClassTypeElement(mon, vn));
			return true;
		case Schema.TYPE_NON_PRIMITIVE_NESTED_INTERFACE:
			nestedInterface.add(new InterfaceTypeElement(mon, vn));
			return true;
		case Schema.TYPE_NON_PRIMITIVE_NESTED_ENUM:
			nestedEnum.add(new EnumTypeElement(mon, vn.cloneNav()));
			return true;
		case Schema.TYPE_NON_PRIMITIVE_NESTED_ANNOTATION:
			nestedAnnotation.add(new AnnotationTypeElement(mon, vn));
			return true;
		case Schema.TYPE_NON_PRIMITIVE_CONSTRUCTOR:
			constructor.add(new ConstructorElement(mon, vn));
			return true;
		case Schema.TYPE_NON_PRIMITIVE_IMPLEMENTS:
			implement.add(Long.parseLong(vn.toString(vn.getText()), Schema.RADIX));
			return true;
		case Schema.TYPE_NON_PRIMITIVE_EXTENDS:
			extend = Long.parseLong(vn.toString(vn.getText()), Schema.RADIX);
			return true;
		default:
			return false;
		}
	}

	public NonPrimitiveTypeElement(long id, String name) {
		super(id, name);
	}

	public NonPrimitiveTypeElement(String name) {
		super(name);
	}

	public Set<FieldVarElement> getField() {
		return field;
	}

	public Set<MethodElement> getMethod() {
		return method;
	}

	public long getExtend() {
		return extend;
	}

	public void setExtend(long extend) {
		this.extend = extend;
	}

	public Set<Long> getImplement() {
		return implement;
	}

	public Set<ClassTypeElement> getNestedClass() {
		return nestedClass;
	}

	public Set<InterfaceTypeElement> getNestedInterface() {
		return nestedInterface;
	}

	public Set<EnumTypeElement> getNestedEnum() {
		return nestedEnum;
	}

	public Set<AnnotationTypeElement> getNestedAnnotation() {
		return nestedAnnotation;
	}

	public Set<ConstructorElement> getConstructor(){
		return constructor;
	}
	
	@Override
	public void doConvert(StringBuilder sb, String name){
		if(extend != NOT_DEFINED){
			createNode(sb, Schema.TYPE_NON_PRIMITIVE_EXTENDS,Long.toString(extend, Schema.RADIX));
		}
		
		for(Long l : implement){
			createNode(sb, Schema.TYPE_NON_PRIMITIVE_IMPLEMENTS, Long.toString(l, Schema.RADIX));
		}
		
		for (ConstructorElement e : constructor) {
			e.convert(sb, Schema.TYPE_NON_PRIMITIVE_CONSTRUCTOR);
		}
			
		for (MethodElement e : method) {
			e.convert(sb, Schema.TYPE_NON_PRIMITIVE_METHOD);
		}
		
		for (FieldVarElement e : field) {
			e.convert(sb, Schema.TYPE_NON_PRIMITIVE_FIELD);
		}

		for (NonPrimitiveTypeElement e : nestedClass) {
			e.convert(sb, Schema.TYPE_NON_PRIMITIVE_NESTED_CLASS);
		}
		
		for (NonPrimitiveTypeElement e : nestedInterface) {
			e.convert(sb, Schema.TYPE_NON_PRIMITIVE_NESTED_INTERFACE);
		}
		
		for (NonPrimitiveTypeElement e : nestedEnum) {
			e.convert(sb, Schema.TYPE_NON_PRIMITIVE_NESTED_ENUM);
		}

		for (NonPrimitiveTypeElement e : nestedAnnotation) {
			e.convert(sb, Schema.TYPE_NON_PRIMITIVE_NESTED_ANNOTATION);
		}
	}
	
	@Override
	public int subtreeSize() {
		int size = 1;
		for(XMLConvertable dc : field) size += dc.subtreeSize();
		for(XMLConvertable dc : method) size += dc.subtreeSize();
		for(XMLConvertable dc : nestedClass) size += dc.subtreeSize();
		for(XMLConvertable dc : nestedInterface) size += dc.subtreeSize();
		for(XMLConvertable dc : nestedEnum) size += dc.subtreeSize();
		for(XMLConvertable dc : nestedAnnotation) size += dc.subtreeSize();
		for(XMLConvertable dc : constructor) size += dc.subtreeSize();
		return size;
	}
}
