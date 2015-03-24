package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;


public class ConstructorElement extends MethodElement {
	
	public ConstructorElement(IProgressMonitor mon, VTDNav vn) throws NumberFormatException, NavException, ParseException {
		super(mon, vn);
		parse();
	}

	public ConstructorElement(String name) {
		super(name);
	}
	
	public ConstructorElement(long id, String name) {
		super(id, name);
	}
}
