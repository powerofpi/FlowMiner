package edu.iastate.flowminer.io.model;

import java.text.ParseException;
import java.util.Set;

import net.ontopia.utils.CompactHashSet;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.PilotException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public class EnumTypeElement extends NonPrimitiveTypeElement {
	private Set<EnumConstantElement> enumConstants = new CompactHashSet<EnumConstantElement>();
	
	public EnumTypeElement(IProgressMonitor mon, VTDNav vn) throws PilotException, NavException, ParseException {
		super(mon, vn);
		parse();
	}

	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		if(super.interpretChild(mon, vn, name)) return true;
		switch (name) {
		case Schema.ENUM_TYPE_NON_PRIMITIVE_CONSTANT:
			enumConstants.add(new EnumConstantElement(mon, vn));
			return true;
		default:
			return false;
		}
	}
	
	public EnumTypeElement(long id, String name) {
		super(id, name);
	}

	public EnumTypeElement(String name) {
		super(name);
	}
	
	@Override
	public void releaseMemory(){
		super.releaseMemory();
		enumConstants = null;
	}
	
	public Set<EnumConstantElement> getEnumConstant(){
		return enumConstants;
	}

	@Override
	public void doConvert(StringBuilder sb, String name){
		super.doConvert(sb, name);
		for (EnumConstantElement e : enumConstants) {
			e.convert(sb, Schema.ENUM_TYPE_NON_PRIMITIVE_CONSTANT);
		}
	}
	
	@Override
	public int subtreeSize() {
		return super.subtreeSize() + enumConstants.size();
	}
}
