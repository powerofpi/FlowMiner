package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.Schema;

public class Attribute extends XMLConvertable{
	private String key, value;
	
	public Attribute(String key, String value){
		this.key = key;
		this.value = value;
	}
	
	@Override
	public void releaseMemory(){
		key = null;
		value = null;
	}
	
	@Override
	public boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException{
		switch (name) {
		case Schema.ATTR_KEY:
			key = vn.toString(vn.getText());
			return true;
		case Schema.ATTR_VAL:
			value = vn.toString(vn.getText());
			return true;
		default:
			return false;
		}
	}
	
	public Attribute(){
		
	}
	
	public Attribute(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}

	@Override
	public void convert(StringBuilder sb, String name) {
		startNode(sb, name);
		createNode(sb, Schema.ATTR_KEY, key);
		createNode(sb, Schema.ATTR_VAL, value);
		endNode(sb, name);
	}
	
	@Override
	public int subtreeSize() {
		return 0;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}