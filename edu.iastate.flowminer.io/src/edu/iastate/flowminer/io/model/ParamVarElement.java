package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public class ParamVarElement extends VarElement {
	private int param_idx = NOT_DEFINED;

	public ParamVarElement(IProgressMonitor mon, VTDNav vn) throws NumberFormatException, NavException, ParseException {
		super(mon, vn);
		parse();
	}
	
	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		if(super.interpretChild(mon, vn, name)) return true;
		switch (name) {
		case Schema.PARAM_VAR_INDEX:
			param_idx = Integer.parseInt(vn.toString(vn.getText()), Schema.RADIX);
			return true;
		default:
			return false;
		}
	}

	public ParamVarElement(long id, String name) {
		super(id, name);
	}

	public ParamVarElement(String name) {
		super(name);
	}

	public int getParam_idx() {
		return param_idx;
	}

	public void setParam_idx(int param_idx) {
		this.param_idx = param_idx;
	}

	@Override
	public void doConvert(StringBuilder sb, String name){
		super.doConvert(sb, name);
		createNode(sb, Schema.PARAM_VAR_INDEX, Integer.toString(param_idx, Schema.RADIX));
	}
}