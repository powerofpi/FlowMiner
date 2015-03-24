package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;


public class FieldVarElement extends VarElement {
	public FieldVarElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}

	public FieldVarElement(long id, String name) {
		super(id, name);
	}

	public FieldVarElement(String name) {
		super(name);
	}
}
