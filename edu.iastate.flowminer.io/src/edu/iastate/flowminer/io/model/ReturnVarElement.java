package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;


public class ReturnVarElement extends VarElement {
	public ReturnVarElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}

	public ReturnVarElement(long id, String name) {
		super(id, name);
	}

	public ReturnVarElement(String name) {
		super(name);
	}
}
