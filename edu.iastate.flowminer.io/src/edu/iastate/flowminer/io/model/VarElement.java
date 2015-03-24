package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public abstract class VarElement extends Element {
	private int array_dim = NOT_DEFINED;
	private long type;

	public VarElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
	}
	
	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		if(super.interpretChild(mon, vn, name)) return true;
		switch (name) {
		case Schema.VAR_ARRAY_DIM:
			array_dim = Integer.parseInt(vn.toString(vn.getText()), Schema.RADIX);
			return true;
		case Schema.VAR_TYPE:
			type = Long.parseLong(vn.toString(vn.getText()), Schema.RADIX);
			return true;
		default:
			return false;
		}
	}

	public VarElement(long id, String name) {
		super(id, name);
	}

	public VarElement(String name) {
		super(name);
	}

	public int getArray_dim() {
		return array_dim;
	}

	public void setArray_dim(int array_dim) {
		this.array_dim = array_dim;
	}

	public void setType(long type) {
		this.type = type;
	}
	
	public long getType() {
		return type;
	}
	
	@Override
	public void doConvert(StringBuilder sb, String name){
		createNode(sb, Schema.VAR_TYPE, Long.toString(type, Schema.RADIX));
		
		if (array_dim > NOT_DEFINED) {
			createNode(sb, Schema.VAR_ARRAY_DIM, Integer.toString(array_dim, Schema.RADIX));
		}
	}
	
	@Override
	public int subtreeSize() {
		return 1;
	}
}
