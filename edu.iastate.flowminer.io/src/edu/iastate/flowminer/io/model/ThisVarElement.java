package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

public class ThisVarElement extends VarElement {
	public ThisVarElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}
	
	public ThisVarElement(long id, String name) {
		super(id, name);
	}

	public ThisVarElement(String name) {
		super(name);
	}
}
