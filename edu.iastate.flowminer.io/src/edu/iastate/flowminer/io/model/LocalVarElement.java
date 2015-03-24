package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public class LocalVarElement extends VarElement {
	String type;
	int paramIdx = -1;
	
	public LocalVarElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}

	public LocalVarElement(long id, String name, String type) {
		super(id, name);
		this.type = type;
	}

	public LocalVarElement(String name, String type) {
		super(name);
		this.type = type;
	}
	
	public String getSchemaType(){
		return type;
	}
	
	public void setSchemaType(String type){
		this.type = type;
	}
	
	public int getParamIdx(){
		return paramIdx;
	}
	
	public void setParamIdx(int paramIdx){
		this.paramIdx = paramIdx;
	}
	
	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		if(super.interpretChild(mon, vn, name)) return true;
		switch (name) {
		case Schema.LOCAL_VAR_SCHEMA_TYPE:
			type = vn.toString(vn.getText());
			return true;
		case Schema.PARAM_VAR_INDEX:
			paramIdx = Integer.parseInt(vn.toString(vn.getText()), Schema.RADIX);
			return true;
		default:
			return false;
		}
	}
	
	@Override
	public void doConvert(StringBuilder sb, String name){
		super.doConvert(sb, name);
		createNode(sb, Schema.LOCAL_VAR_SCHEMA_TYPE, type);
		
		if(paramIdx > -1){
			createNode(sb, Schema.PARAM_VAR_INDEX, Integer.toString(paramIdx, Schema.RADIX));
		}
	}
}
