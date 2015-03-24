package edu.iastate.flowminer.io.model;

import java.text.ParseException;
import java.util.Set;

import net.ontopia.utils.CompactHashSet;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public class PackageElement extends Element {
	private Set<ClassTypeElement> type_class = new CompactHashSet<ClassTypeElement>();
	private Set<InterfaceTypeElement> type_interface = new CompactHashSet<InterfaceTypeElement>();
	private Set<EnumTypeElement> type_enum = new CompactHashSet<EnumTypeElement>();
	private Set<AnnotationTypeElement> type_annotation = new CompactHashSet<AnnotationTypeElement>();

	public PackageElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}
	
	@Override
	public void releaseMemory(){
		super.releaseMemory();
		type_class = null;
		type_interface = null;
		type_enum = null;
		type_annotation = null;
	}

	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		if(super.interpretChild(mon, vn, name)) return true;
		switch (name) {
		case Schema.PACKAGE_CLASS:
			type_class.add(new ClassTypeElement(mon, vn));
			return true;
		case Schema.PACKAGE_INTERFACE:
			type_interface.add(new InterfaceTypeElement(mon, vn));
			return true;
		case Schema.PACKAGE_ENUM:
			type_enum.add(new EnumTypeElement(mon, vn));
			return true;
		case Schema.PACKAGE_ANNOTATION:
			type_annotation.add(new AnnotationTypeElement(mon, vn));
			return true;
		default:
			return false;
		}
	}
	
	public PackageElement(long id, String name) {
		super(id, name);
	}

	public PackageElement(String name) {
		super(name);
	}
	
	public Set<ClassTypeElement> getType_class() {
		return type_class;
	}

	public Set<InterfaceTypeElement> getType_interface() {
		return type_interface;
	}

	public Set<EnumTypeElement> getType_enum() {
		return type_enum;
	}

	public Set<AnnotationTypeElement> getType_annotation() {
		return type_annotation;
	}
	
	@Override
	public void doConvert(StringBuilder sb, String name){
		for (ClassTypeElement e : type_class) {
			e.convert(sb, Schema.PACKAGE_CLASS);
		}
		
		for (InterfaceTypeElement e : type_interface) {
			e.convert(sb, Schema.PACKAGE_INTERFACE);
		}
		
		for (EnumTypeElement e : type_enum) {
			e.convert(sb, Schema.PACKAGE_ENUM);
		}
		
		for (AnnotationTypeElement e : type_annotation) {
			e.convert(sb, Schema.PACKAGE_ANNOTATION);
		}
	}
	
	@Override
	public int subtreeSize() {
		int size = 1;
		for(XMLConvertable dc : type_class) size += dc.subtreeSize();
		for(XMLConvertable dc : type_interface) size += dc.subtreeSize();
		for(XMLConvertable dc : type_enum) size += dc.subtreeSize();
		for(XMLConvertable dc : type_annotation) size += dc.subtreeSize();
		return size;
	}
}
